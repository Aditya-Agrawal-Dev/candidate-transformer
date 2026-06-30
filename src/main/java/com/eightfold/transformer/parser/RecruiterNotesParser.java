package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.ExtractionMethod;
import com.eightfold.transformer.model.SourceType;
import com.eightfold.transformer.source.RawCandidateData;
import com.eightfold.transformer.source.RawValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses free-text recruiter scratch notes (.txt). Lowest-trust source in the
 * merge priority order, but still useful corroborating/supplementary signal
 * (e.g. "candidate confirmed she's open to relocating to Austin, TX").
 */
public final class RecruiterNotesParser implements SourceParser {

    private static final Logger log = LoggerFactory.getLogger(RecruiterNotesParser.class);

    private static final Pattern LOCATION_HINT = Pattern.compile(
            "(?i)(?:based in|located in|relocat\\w* to|lives in)\\s+([A-Za-z .]+?),\\s*([A-Za-z]{2,})(?:\\s*,\\s*([A-Za-z .]+))?(?=[.,\\n]|$)"
    );

    @Override
    public boolean supports(Path file) {
        return file.toString().toLowerCase().endsWith(".txt");
    }

    @Override
    public RawCandidateData parse(Path file) {
        String sourceId = file.getFileName().toString();
        RawCandidateData data = new RawCandidateData(SourceType.RECRUITER_NOTES, sourceId);

        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            log.warn("Could not read recruiter notes {}: {}", file, e.getMessage());
            data.markMalformed("I/O error reading notes: " + e.getMessage());
            return data;
        }

        if (text.isBlank()) {
            data.markMalformed("Recruiter notes file is empty");
            return data;
        }

        FreeTextHeuristics.extractContactInfo(data, text, ExtractionMethod.REGEX_EXTRACTION);
        FreeTextHeuristics.extractSkills(data, text, ExtractionMethod.KEYWORD_EXTRACTION);

        Matcher m = LOCATION_HINT.matcher(text);
        if (m.find()) {
            String city = m.group(1).trim();
            String region = m.group(2).trim();
            data.setCity(new RawValue<>(city, ExtractionMethod.REGEX_EXTRACTION + " (location hint phrase)"));
            data.setRegion(new RawValue<>(region, ExtractionMethod.REGEX_EXTRACTION + " (location hint phrase)"));
            if (m.group(3) != null) {
                data.setCountry(new RawValue<>(m.group(3).trim(), ExtractionMethod.REGEX_EXTRACTION + " (location hint phrase)"));
            }
        }

        return data;
    }
}
