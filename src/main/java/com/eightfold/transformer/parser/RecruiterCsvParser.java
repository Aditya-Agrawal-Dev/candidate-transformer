package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.ExtractionMethod;
import com.eightfold.transformer.model.SourceType;
import com.eightfold.transformer.source.RawCandidateData;
import com.eightfold.transformer.source.RawExperience;
import com.eightfold.transformer.source.RawValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecruiterCsvParser implements SourceParser {

    private static final Logger log = LoggerFactory.getLogger(RecruiterCsvParser.class);

    @Override
    public boolean supports(Path file) {
        return file.toString().toLowerCase().endsWith(".csv");
    }

    @Override
    public RawCandidateData parse(Path file) {
        String sourceId = file.getFileName().toString();
        RawCandidateData data = new RawCandidateData(SourceType.RECRUITER_CSV, sourceId);

        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            log.warn("Could not read recruiter CSV {}: {}", file, e.getMessage());
            data.markMalformed("I/O error reading CSV: " + e.getMessage());
            return data;
        }

        if (lines.isEmpty()) {
            data.markMalformed("CSV file is empty");
            return data;
        }

        List<String> header = splitCsvLine(lines.get(0));
        Map<String, Integer> columnIndex = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columnIndex.put(normalizeHeader(header.get(i)), i);
        }

// Validate that this looks like a recruiter CSV.
        List<String> requiredColumns = List.of("name", "email");

        for (String column : requiredColumns) {
            if (!columnIndex.containsKey(column)) {
                data.markMalformed("Invalid recruiter CSV: missing required column '" + column + "'");
                return data;
            }
        }

        boolean anyRowParsed = false;
        for (int lineNo = 1; lineNo < lines.size(); lineNo++) {
            String line = lines.get(lineNo);
            if (line.isBlank()) {
                continue;
            }
            List<String> fields = splitCsvLine(line);
            if (fields.isEmpty()) {
                continue;
            }
            String method = ExtractionMethod.CSV_COLUMN_MAPPING + " (row " + (lineNo + 1) + ")";

            String name = cell(fields, columnIndex, "name");
            if (notBlank(name)) {
                data.setFullName(new RawValue<>(name, method));
            }

            String email = cell(fields, columnIndex, "email");
            if (notBlank(email)) {
                data.addEmail(new RawValue<>(email, method));
            }

            String phone = cell(fields, columnIndex, "phone");
            if (notBlank(phone)) {
                data.addPhone(new RawValue<>(phone, method));
            }

            String company = cell(fields, columnIndex, "current_company");
            String title = cell(fields, columnIndex, "title");
            if (notBlank(company) || notBlank(title)) {
                data.addExperience(new RawValue<>(
                        new RawExperience(emptyToNull(company), emptyToNull(title), null, "present", null),
                        method));
                if (notBlank(title)) {
                    data.setHeadline(new RawValue<>(title, method));
                }
            }

            String city = cell(fields, columnIndex, "city");
            if (notBlank(city)) {
                data.setCity(new RawValue<>(city, method));
            }
            String region = cell(fields, columnIndex, "region");
            if (notBlank(region)) {
                data.setRegion(new RawValue<>(region, method));
            }
            String country = cell(fields, columnIndex, "country");
            if (notBlank(country)) {
                data.setCountry(new RawValue<>(country, method));
            }

            anyRowParsed = true;
        }

        if (!anyRowParsed) {
            data.markMalformed("CSV had a header but no usable data rows");
        }

        return data;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String emptyToNull(String s) {
        return notBlank(s) ? s : null;
    }

    private String cell(List<String> fields, Map<String, Integer> columnIndex, String column) {
        Integer idx = columnIndex.get(column);
        if (idx == null || idx >= fields.size()) {
            return null;
        }
        String v = fields.get(idx);
        return v == null ? null : v.trim();
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase().replace(" ", "_");
    }


    private List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        result.add(current.toString());
        return result;
    }
}
