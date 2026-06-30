package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.ExtractionMethod;
import com.eightfold.transformer.model.SourceType;
import com.eightfold.transformer.source.RawCandidateData;
import com.eightfold.transformer.source.RawEducation;
import com.eightfold.transformer.source.RawExperience;
import com.eightfold.transformer.source.RawValue;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a resume in PDF, DOCX, or plain-text form into raw candidate data using
 * regex/keyword heuristics (this is "REGEX_EXTRACTION + section heuristics", not
 * a full NLP/NER pipeline - acceptable and explicit tradeoff for this assignment,
 * documented in the design doc).
 * <p>
 * Factory Pattern: {@link ParserFactory} hands back the right extraction strategy
 * (PDFBox / POI / plain read) based on file extension; this class then runs the
 * SAME free-text heuristics over the extracted text regardless of original format.
 */
public final class ResumeParser implements SourceParser {

    private static final Logger log = LoggerFactory.getLogger(ResumeParser.class);

    // "Company — Title | Mon YYYY - Mon YYYY" or "Title at Company (YYYY - YYYY)" style lines.
    private static final Pattern EXPERIENCE_LINE = Pattern.compile(
            "^(?<title>[A-Za-z0-9&/.,'+ ]{2,60})\\s+(?:at|@|[-–—|])\\s+(?<company>[A-Za-z0-9&/.,' ]{2,60})\\s*[\\(\\[]?\\s*(?<start>[A-Za-z]{3,9}\\.?\\s*\\d{4}|\\d{4})\\s*[-–—to]+\\s*(?<end>[A-Za-z]{3,9}\\.?\\s*\\d{4}|\\d{4}|[Pp]resent|[Cc]urrent)\\s*[\\)\\]]?\\s*$"
    );

    // "Degree, Institution, YYYY" or "Institution — Degree (YYYY)"
    private static final Pattern EDUCATION_LINE = Pattern.compile(
            "^(?<degree>[A-Za-z0-9.&' ]{2,50}?)\\s*,\\s*(?<institution>[A-Za-z0-9&'. ]{2,60})\\s*,?\\s*(?<year>\\d{4})\\s*$"
    );

    @Override
    public boolean supports(Path file) {
        String lower = file.toString().toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".txt");
    }

    @Override
    public RawCandidateData parse(Path file) {
        String sourceId = file.getFileName().toString();
        RawCandidateData data = new RawCandidateData(SourceType.RESUME, sourceId);

        String text;
        try {
            text = extractText(file);
        } catch (Exception e) {
            log.warn("Failed to extract text from resume {}: {}", file, e.getMessage());
            data.markMalformed("Could not extract text: " + e.getMessage());
            return data;
        }

        if (text == null || text.isBlank()) {
            data.markMalformed("Resume contained no extractable text");
            return data;
        }

        String nameGuess = FreeTextHeuristics.guessName(text);
        if (nameGuess != null) {
            data.setFullName(new RawValue<>(nameGuess, ExtractionMethod.FREE_TEXT_NER_HEURISTIC + " (first name-shaped line)"));
        }
        // First non-blank line right after the name, if short and not contact info, is often the headline.
        String headlineGuess = guessHeadline(text, nameGuess);
        if (headlineGuess != null) {
            data.setHeadline(new RawValue<>(headlineGuess, ExtractionMethod.HEADER_SECTION_HEURISTIC.toString()));
        }

        FreeTextHeuristics.extractContactInfo(data, text, ExtractionMethod.REGEX_EXTRACTION);
        FreeTextHeuristics.extractLabeledSkillsSection(data, text, ExtractionMethod.KEYWORD_EXTRACTION);
        FreeTextHeuristics.extractSkills(data, text, ExtractionMethod.KEYWORD_EXTRACTION);

        extractExperience(data, text);
        extractEducation(data, text);

        return data;
    }

    private String guessHeadline(String text, String nameGuess) {
        String[] lines = text.split("\\r?\\n");
        boolean afterName = nameGuess == null;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (!afterName) {
                if (line.equals(nameGuess)) {
                    afterName = true;
                }
                continue;
            }
            if (line.contains("@") || line.matches(".*\\d{3}.*") || line.length() > 80) {
                return null; // next line is contact info or too long, no clean headline
            }
            return line;
        }
        return null;
    }

    private void extractExperience(RawCandidateData data, String text) {
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            Matcher m = EXPERIENCE_LINE.matcher(line);
            if (m.matches()) {
                data.addExperience(new RawValue<>(
                        new RawExperience(m.group("company").trim(), m.group("title").trim(),
                                m.group("start").trim(), m.group("end").trim(), null),
                        ExtractionMethod.REGEX_EXTRACTION + " (experience line pattern)"));
            }
        }
    }

    private void extractEducation(RawCandidateData data, String text) {
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            Matcher m = EDUCATION_LINE.matcher(line);
            if (m.matches()) {
                data.addEducation(new RawValue<>(
                        new RawEducation(m.group("institution").trim(), m.group("degree").trim(), null, m.group("year").trim()),
                        ExtractionMethod.REGEX_EXTRACTION + " (education line pattern)"));
            }
        }
    }

    private String extractText(Path file) throws IOException {
        String lower = file.toString().toLowerCase();
        if (lower.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(file.toFile())) {
                return new PDFTextStripper().getText(doc);
            }
        } else if (lower.endsWith(".docx")) {
            try (FileInputStream fis = new FileInputStream(file.toFile());
                 XWPFDocument doc = new XWPFDocument(fis)) {
                StringBuilder sb = new StringBuilder();
                for (XWPFParagraph p : doc.getParagraphs()) {
                    sb.append(p.getText()).append('\n');
                }
                return sb.toString();
            }
        } else {
            return Files.readString(file);
        }
    }
}
