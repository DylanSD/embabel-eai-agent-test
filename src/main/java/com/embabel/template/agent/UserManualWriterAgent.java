package com.embabel.template.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.HasContent;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.core.types.Timestamped;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

abstract class WriterPersonas {
    static final RoleGoalBackstory TECHNICAL_WRITER = RoleGoalBackstory
            .withRole("Technical Writer")
            .andGoal("Generate a clear, structured user manual")
            .andBackstory("Has 10 years experience creating manuals for hardware and software products");
}

record ManualSection(String title, String content) implements HasContent {
    @Override
    public String getContent() {
        return "## " + title + "\n\n" + content;
    }
}

record UserManual(List<ManualSection> sections) implements HasContent, Timestamped {
    @Override
    @NonNull
    public Instant getTimestamp() {
        return Instant.now();
    }

    @Override
    public String getContent() {
        return sections.stream()
                .map(ManualSection::getContent)
                .collect(Collectors.joining("\n\n")) +
                "\n\n---\nGenerated on " +
                getTimestamp().atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"));
    }
}

//@Agent(description = "Reads PDFs and generates a structured user manual")
@Profile("!test")
class UserManualWriterAgent {

    private final int sectionWordLimit;

    UserManualWriterAgent(@Value("${manual.sectionWordLimit:300}") int sectionWordLimit) {
        this.sectionWordLimit = sectionWordLimit;
    }

    /**
     * Reads PDF files and generates a structured user manual.
     */
    @AchievesGoal(
            description = "The user manual has been generated from provided PDFs",
            export = @Export(remote = true, name = "generateUserManual"))
    @Action
    UserManual generateManual(UserInput userInput, OperationContext context) throws IOException {
        // Assume input is list of file paths (local or mounted Google Drive)
        List<String> pdfPaths = List.of(userInput.getContent().split(","));

        // Extract text from PDFs
        List<String> pdfTexts = pdfPaths.stream()
                .map(Path::of)
                .map(this::extractPdfTextSafe)
                .toList();

        // Use AI to summarize each PDF into a manual section
        List<ManualSection> sections = pdfTexts.stream()
                .map(pdfContent -> context.ai()
                        .withLlm(LlmOptions.withAutoLlm().withTemperature(0.3))
                        .withPromptContributor(WriterPersonas.TECHNICAL_WRITER)
                        .createObject(String.format("""
                                        Summarize the following document into a concise, structured manual section.
                                        Limit to ~%d words.
                                        Provide a clear title and practical instructions if applicable.

                                        # Document Content
                                        %s
                                        """,
                                sectionWordLimit,
                                pdfContent
                        ).trim(), ManualSection.class)
                ).toList();

        return new UserManual(sections);
    }

    private String extractPdfTextSafe(Path path) {
        try {
            // You’d integrate Apache PDFBox or similar here
            return Files.readString(path); // placeholder (only works if PDF is already plain text)
        } catch (IOException e) {
            return "ERROR reading PDF: " + path.getFileName();
        }
    }
}
