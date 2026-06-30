package com.eightfold.transformer.parser;

/**
 * Factory Pattern: produces the correct {@link SourceParser} strategy.
 * <p>
 * Note that resumes and recruiter notes can both be plain {@code .txt} files, so
 * format alone is not enough to disambiguate them - the CLI/pipeline tells the
 * factory which ROLE a given file plays (its {@code --resume} vs {@code --notes}
 * flag), and the factory returns the parser for that role. Format detection
 * (.pdf/.docx/.txt) happens *inside* {@link ResumeParser} itself.
 */
public final class ParserFactory {

    private final RecruiterCsvParser csvParser = new RecruiterCsvParser();
    private final AtsJsonParser atsJsonParser = new AtsJsonParser();
    private final ResumeParser resumeParser = new ResumeParser();
    private final RecruiterNotesParser notesParser = new RecruiterNotesParser();

    public SourceParser forRecruiterCsv() {
        return csvParser;
    }

    public SourceParser forAtsJson() {
        return atsJsonParser;
    }

    public SourceParser forResume() {
        return resumeParser;
    }

    public SourceParser forRecruiterNotes() {
        return notesParser;
    }
}
