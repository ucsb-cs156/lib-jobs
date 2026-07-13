package edu.ucsb.cs156.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Guards against a real regression (v0.1.0-v0.1.4): {@code swagger-annotations-jakarta} was a plain
 * compile dependency, so its pinned version won Maven's nearest-wins mediation over a consuming
 * app's own (deeper, newer) springdoc-supplied version, breaking that app's OpenAPI doc generation
 * entirely (NoSuchMethodError on {@code Parameter.validationGroups()}).
 *
 * <p>This can't be caught by a runtime test inside this module: {@code provided} scope only affects
 * what's exported to downstream consumers, so a same-reactor test recreates the identical conflict
 * regardless of the fix (verified empirically both ways while diagnosing this bug). The actual fix
 * was verified by booting a real consumer (proj-scaffold) against the built artifact. This test
 * instead pins the pom.xml declaration itself, so the fix can't silently regress.
 */
public class PomDependencyScopeTests {

  @Test
  public void swagger_annotations_jakarta_must_stay_provided_scope() throws Exception {
    Document pom =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File("pom.xml"));

    XPath xpath = XPathFactory.newInstance().newXPath();
    Node scopeNode =
        (Node)
            xpath.evaluate(
                "//*[local-name()='dependency']"
                    + "[*[local-name()='artifactId']='swagger-annotations-jakarta']"
                    + "/*[local-name()='scope']",
                pom,
                XPathConstants.NODE);

    assertNotNull(
        scopeNode,
        "swagger-annotations-jakarta must declare a <scope>; if this dependency was removed"
            + " entirely, this test should be removed too");
    assertEquals(
        "provided",
        scopeNode.getTextContent(),
        "swagger-annotations-jakarta must stay 'provided' - a normal compile dependency here"
            + " wins Maven's nearest-wins mediation over a consuming app's own springdoc-supplied"
            + " version, breaking that app's OpenAPI doc generation (see class javadoc)");
  }
}
