package dev.commands;

import dev.LLMService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

@Command(name = "explain", aliases = {"-e", "--explain"}, mixinStandardHelpOptions = true, description = "Explain a stack trace or error using Claude")
public class ExplainCmd implements Runnable {
    @Parameters(description = "File to analyze (omit to read from stdin)", arity = "0..1")
    private String filePath;

    @Override
    public void run() {
        try {
            String text;
            if (filePath != null && !filePath.isBlank()) {
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    System.err.println("File not found: " + filePath);
                    return;
                }
                text = Files.readString(path);
            } else {
                if (System.in.available() == 0) {
                    System.err.println("Provide a file path or pipe input: cat error.log | recall explain");
                    return;
                }
                try (Scanner s = new Scanner(System.in).useDelimiter("\\A")) {
                    text = s.hasNext() ? s.next() : "";
                }
            }

            if (text.isBlank()) {
                System.err.println("No input to analyze.");
                return;
            }

            String prompt = String.format(
                "Analyze this error and return:\n**Root Cause:** what caused it\n**Fix:** how to fix it\n\n%s",
                text.length() > 12000 ? text.substring(0, 12000) + "\n... (truncated)" : text);

            System.out.println("Analyzing...");
            LLMService llm = new LLMService();
            String explanation = llm.query(prompt,
                "You are a senior Spring engineer. Be concise and actionable. No fluff.",
                2048);
            System.out.println(explanation);
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}