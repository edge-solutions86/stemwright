package io.github.rkaushikdev.stemwright.tooling;

import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Checks that the three places a version is written agree with one another, and that the build
 * still makes the jar people run.
 *
 * <p>They drift, always in the same way: the version in {@code pom.xml} is bumped as part of a
 * release and the constant the cli prints is not, so a support conversation starts with a version
 * number that names a build nobody shipped.
 *
 * <p>Run it with {@code java -cp target/classes:target/test-classes} and this class after {@code
 * ./mvnw -q test-compile}. It exits non zero and prints one line per problem when anything
 * disagrees.
 */
public final class Release {

    /** The file that declares the version the cli reports. */
    public static final String VERSION_SOURCE = "src/main/java/io/github/rkaushikdev/stemwright/bin/Stemwright.java";

    /** The classifier of the self contained jar the shade plugin attaches. */
    public static final String CLI_CLASSIFIER = "cli";

    private static final String SOURCES = "src/main/java";
    private static final String SHADE_PLUGIN = "maven-shade-plugin";
    private static final String MANIFEST_TRANSFORMER = "ManifestResourceTransformer";
    private static final String DEFAULT_CLASSIFIER = "shaded";

    private static final Pattern VERSION_CONSTANT =
            Pattern.compile("public static final String VERSION = \"([^\"]+)\";");
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    private Release() {}

    /**
     * A jar the build attaches beside the library jar.
     *
     * @param name its classifier
     * @param mainClass the class its manifest runs, when it names one
     */
    public record Binary(String name, Optional<String> mainClass) {

        public Binary {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(mainClass, "mainClass");
        }
    }

    /**
     * What a release check found.
     *
     * @param ok whether nothing disagreed
     * @param problems one sentence per disagreement, in the order they were checked
     * @param version the version {@code pom.xml} declares, when it declares one
     */
    public record Result(boolean ok, List<String> problems, Optional<String> version) {

        public Result {
            problems = List.copyOf(problems);
            Objects.requireNonNull(version, "version");
        }
    }

    /** The version the cli declares, read from the source of its entry point. */
    public static Optional<String> versionFromSource(String text) {
        Matcher match = VERSION_CONSTANT.matcher(text);
        return match.find() ? Optional.of(match.group(1)) : Optional.empty();
    }

    /** Whether the changelog has a section for the version, or one for work not yet released. */
    public static boolean changelogMentions(String text, String version) {
        return text.contains("## " + version) || text.contains("## Unreleased");
    }

    /**
     * Parses a {@code pom.xml}. Document type declarations are refused, so nothing in the file can
     * make the parser read anything else.
     *
     * @throws IllegalArgumentException when the text is not well formed
     */
    public static Document parsePom(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (SAXException e) {
            throw new IllegalArgumentException("pom.xml is not well formed: " + e.getMessage(), e);
        } catch (ParserConfigurationException | IOException e) {
            throw new IllegalStateException("could not parse pom.xml", e);
        }
    }

    /**
     * The jars the shade plugin attaches, each with the main class its manifest names and any
     * property reference in it resolved. An execution that replaces the library jar rather than
     * attaching one beside it is not listed.
     */
    public static List<Binary> binariesIn(Document pom) {
        Element project = pom.getDocumentElement();
        Map<String, String> properties = propertiesOf(project);
        List<Binary> binaries = new ArrayList<>();
        for (Element plugin : path(project, "build", "plugins", "plugin")) {
            if (!textOf(plugin, "artifactId").equals(Optional.of(SHADE_PLUGIN))) {
                continue;
            }
            List<Element> shared = path(plugin, "configuration");
            for (Element execution : path(plugin, "executions", "execution")) {
                List<Element> configurations = new ArrayList<>(path(execution, "configuration"));
                configurations.addAll(shared);
                if (!setting(configurations, c -> textOf(c, "shadedArtifactAttached"))
                        .equals(Optional.of("true"))) {
                    continue;
                }
                String name = setting(configurations, c -> textOf(c, "shadedClassifierName"))
                        .orElse(DEFAULT_CLASSIFIER);
                Optional<String> mainClass =
                        setting(configurations, Release::manifestMainClass).map(value -> resolve(value, properties));
                binaries.add(new Binary(name, mainClass));
            }
        }
        return List.copyOf(binaries);
    }

    /**
     * Every disagreement between the build file, the version constant, the changelog and the
     * sources, as one sentence each.
     *
     * @param pom the parsed {@code pom.xml}
     * @param sourceVersion the version the cli declares, if it declares one
     * @param changelog the text of {@code CHANGELOG.md}
     * @param declaredClasses the fully qualified names of the classes under {@code src/main/java}
     */
    public static List<String> checkAgreement(
            Document pom, Optional<String> sourceVersion, String changelog, Set<String> declaredClasses) {
        Element project = pom.getDocumentElement();
        Optional<String> version = textOf(project, "version").filter(value -> !value.isEmpty());
        List<String> problems = new ArrayList<>();

        if (version.isEmpty()) {
            problems.add("pom.xml has no version");
        }
        if (sourceVersion.isEmpty()) {
            problems.add(VERSION_SOURCE + " does not declare a VERSION constant");
        } else if (version.isPresent() && !sourceVersion.equals(version)) {
            problems.add("pom.xml says " + version.get() + " and the cli says " + sourceVersion.get());
        }
        version.filter(value -> !changelogMentions(changelog, value))
                .ifPresent(
                        value -> problems.add("CHANGELOG.md has no section for " + value + " and none for Unreleased"));

        boolean attached = false;
        for (Binary binary : binariesIn(pom)) {
            attached |= binary.name().equals(CLI_CLASSIFIER);
            if (binary.mainClass().isEmpty()) {
                problems.add("the " + binary.name() + " jar names no main class");
            } else if (!declaredClasses.contains(binary.mainClass().get())) {
                problems.add("the " + binary.name() + " jar runs "
                        + binary.mainClass().get() + ", which " + SOURCES + " does not declare");
            }
        }
        if (!attached) {
            problems.add("the shade plugin does not attach a " + CLI_CLASSIFIER + " jar");
        }

        Optional<String> mainClass = Optional.ofNullable(propertiesOf(project).get("main.class"));
        if (mainClass.isEmpty()) {
            problems.add("pom.xml has no main.class property");
        } else if (!declaredClasses.contains(mainClass.get())) {
            problems.add("main.class names " + mainClass.get() + ", which " + SOURCES + " does not declare");
        }

        return List.copyOf(problems);
    }

    /** Reads {@code pom.xml}, the cli's source, {@code CHANGELOG.md} and the sources, and checks them. */
    public static Result checkRelease() {
        Path root = Workspace.root();
        Document pom = parsePom(Workspace.read(root.resolve("pom.xml")));
        String source = Workspace.read(root.resolve(VERSION_SOURCE));
        String changelog = Workspace.read(root.resolve("CHANGELOG.md"));
        List<String> problems = checkAgreement(pom, versionFromSource(source), changelog, classesIn(root));
        Optional<String> version = textOf(pom.getDocumentElement(), "version").filter(value -> !value.isEmpty());
        return new Result(problems.isEmpty(), problems, version);
    }

    /** Checks the release metadata and exits non zero when anything disagrees. */
    public static void main(String[] args) {
        Result result = checkRelease();
        if (result.ok()) {
            System.out.print("version " + result.version().orElseThrow() + " agrees everywhere\n");
            System.out.flush();
            return;
        }
        for (String problem : result.problems()) {
            System.out.print(problem + "\n");
        }
        System.out.flush();
        System.exit(1);
    }

    /** The fully qualified name of every top level class under {@code src/main/java}. */
    private static Set<String> classesIn(Path root) {
        Path sources = root.resolve(SOURCES);
        Set<String> classes = new LinkedHashSet<>();
        for (Path path : Workspace.javaFiles(root, sources)) {
            String key = Workspace.key(sources, path);
            classes.add(key.substring(0, key.length() - ".java".length()).replace('/', '.'));
        }
        return classes;
    }

    private static Map<String, String> propertiesOf(Element project) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (Element block : path(project, "properties")) {
            for (Element property : children(block)) {
                properties.put(property.getTagName(), Text.trim(property.getTextContent()));
            }
        }
        return properties;
    }

    private static Optional<String> manifestMainClass(Element configuration) {
        for (Element transformer : path(configuration, "transformers", "transformer")) {
            if (transformer.getAttribute("implementation").endsWith(MANIFEST_TRANSFORMER)) {
                return textOf(transformer, "mainClass");
            }
        }
        return Optional.empty();
    }

    /** The first configuration that sets the value, the execution's own before the plugin's. */
    private static Optional<String> setting(List<Element> configurations, Function<Element, Optional<String>> reading) {
        for (Element configuration : configurations) {
            Optional<String> value = reading.apply(configuration);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /** Replaces each {@code ${name}} the properties define, and leaves any other as written. */
    private static String resolve(String value, Map<String, String> properties) {
        return PROPERTY_REFERENCE
                .matcher(value)
                .replaceAll(reference ->
                        Matcher.quoteReplacement(properties.getOrDefault(reference.group(1), reference.group())));
    }

    private static Optional<String> textOf(Element parent, String name) {
        return path(parent, name).stream().findFirst().map(element -> Text.trim(element.getTextContent()));
    }

    /** The elements reached by following child names from the parent, in document order. */
    private static List<Element> path(Element parent, String... names) {
        List<Element> level = List.of(parent);
        for (String name : names) {
            List<Element> next = new ArrayList<>();
            for (Element element : level) {
                for (Element child : children(element)) {
                    if (child.getTagName().equals(name)) {
                        next.add(child);
                    }
                }
            }
            level = next;
        }
        return level;
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }
}
