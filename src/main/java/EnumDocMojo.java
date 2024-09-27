import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Mojo(name = "enummd")
public class EnumDocMojo extends AbstractMojo {

	@Parameter(property = "project.basedir",defaultValue = "${project.basedir}", required = true)
	private File baseDirectory;

	@Parameter(property = "outputDirectory", defaultValue = "${project.basedir}/md")
	private File outputDirectory;


	@Override
	public void execute() throws MojoExecutionException {
		try {
			List<File> javaFiles = Files.walk(Paths.get(baseDirectory.getPath()))
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.map(java.nio.file.Path::toFile)
					.collect(Collectors.toList());

			for (File javaFile : javaFiles) {
				String markdownTable = processJavaFile(javaFile);
				if (!markdownTable.isEmpty()) {
					String fileName = javaFile.getName().replace(".java", ".md");
					File markdownFile = new File(outputDirectory, fileName);
					if (!outputDirectory.exists()) {
						outputDirectory.mkdirs();
					}
					Files.write(Paths.get(markdownFile.getPath()), markdownTable.getBytes());
					getLog().info("Markdown file generated: " + markdownFile.getPath());
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error processing Java files", e);
		}
	}

	private String processJavaFile(File javaFile) throws IOException {
		String content = new String(Files.readAllBytes(javaFile.toPath()));

		Pattern enumPattern = Pattern.compile("enum\\s+(\\w+)\\s*\\{([^}]*)}", Pattern.DOTALL);
		Matcher matcher = enumPattern.matcher(content);

		StringBuilder markdownTable = new StringBuilder();
		while (matcher.find()) {
			String enumName = matcher.group(1).trim();  // Название enum
			String enumBody = matcher.group(2).trim();

			Pattern enumConstantPattern = Pattern.compile("/\\*\\*(.*?)\\*/\\s*(\\w+)(\\((.*?)\\))?", Pattern.DOTALL);
			Matcher enumConstantMatcher = enumConstantPattern.matcher(enumBody);

			markdownTable.append("##  ").append(javaFile.getName()).append("\n\n");

			boolean hasEnumValues = false;

			List<String[]> tableRows = new ArrayList<>();
			while (enumConstantMatcher.find()) {
				String rawComment = enumConstantMatcher.group(1).trim();
				String enumConstant = enumConstantMatcher.group(2).trim();
				String enumValue = enumConstantMatcher.group(4) != null ? enumConstantMatcher.group(4).trim() : "";

				if (!enumValue.isEmpty()) {
					hasEnumValues = true;
				}

				String cleanedComment = cleanComment(rawComment);

				tableRows.add(new String[]{enumConstant, enumValue, cleanedComment});
			}

			if (hasEnumValues) {
				markdownTable.append("| ").append(enumName).append(" | Enum Value | Comment |\n");
				markdownTable.append("| --- | --- | --- |\n");
				for (String[] row : tableRows) {
					markdownTable.append("| ").append(row[0]).append(" | ").append(row[1]).append(" | ").append(row[2]).append(" |\n");
				}
			} else {
				markdownTable.append("| ").append(enumName).append(" | Comment |\n");
				markdownTable.append("| --- | --- |\n");
				for (String[] row : tableRows) {
					markdownTable.append("| ").append(row[0]).append(" | ").append(row[2]).append(" |\n");
				}
			}
		}

		return markdownTable.toString();
	}

	private String cleanComment(String rawComment) {
		return rawComment.replaceAll("[/\\*]", "")
				.replaceAll("^\\s*\\*", "")
				.replaceAll("\\s+", " ")
				.trim();
	}
}