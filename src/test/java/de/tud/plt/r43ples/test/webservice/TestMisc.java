/**
 * 
 */
package de.tud.plt.r43ples.test.webservice;

import static org.hamcrest.core.StringContains.containsString;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;

import org.apache.commons.configuration.ConfigurationException;
import org.custommonkey.xmlunit.XMLUnit;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import de.tud.plt.r43ples.exception.InternalErrorException;
import de.tud.plt.r43ples.management.Config;
import de.tud.plt.r43ples.management.DataSetGenerationResult;
import de.tud.plt.r43ples.management.SampleDataSet;
import de.tud.plt.r43ples.triplestoreInterface.TripleStoreInterfaceSingleton;
import de.tud.plt.r43ples.webservice.Misc;

/**
 * @author Markus Graube
 *
 */
public class TestMisc extends JerseyTest {
	
	private static DataSetGenerationResult dataset;

	@Override
    protected Application configure() {
        return new ResourceConfig(Misc.class);
    }
    
	@BeforeClass
	public static void setUpBeforeClass() throws ConfigurationException, URISyntaxException, IOException, InternalErrorException {
		XMLUnit.setIgnoreWhitespace(true);
		Config.readConfig("r43ples.test.conf");
		dataset = SampleDataSet.createSampleDataset1();
	}
	
	@AfterClass
	public static void tearDownAfterClass() {
		TripleStoreInterfaceSingleton.close();
	}
	
	/**
	 * Test method for {@link de.tud.plt.r43ples.webservice.Misc#getLandingPage()}.
	 */
	@Test
	public final void testGetLandingPage() {
		String result = target().request(MediaType.TEXT_HTML).get(String.class);
		Assert.assertThat(result, containsString("html"));
	}

	/**
	 * Test method for {@link de.tud.plt.r43ples.webservice.Misc#createSampleDataset(java.lang.String)}.
	 */
	@Test
	public final void testCreateSampleDataset() {
		String result = target("createSampleDataset").request().get(String.class);
		Assert.assertThat(result, containsString(dataset.graphName));
	}

	/**
	 * Test method for {@link de.tud.plt.r43ples.webservice.Misc#getRevisionGraph(java.lang.String, java.lang.String, java.lang.String)}.
	 */
	@Test
	public final void testGetRevisionGraph() {
		String result = target("revisiongraph").queryParam("graph", dataset.graphName).queryParam("format", "text/turtle").request().get(String.class);
		Assert.assertThat(result, containsString("@prefix"));
		
		result = target("revisiongraph").queryParam("graph", dataset.graphName).queryParam("format", "d3").request().get(String.class);
		
		result = target("revisiongraph").queryParam("graph", dataset.graphName).queryParam("format", "batik").request().get(String.class);
		
		result = target("revisiongraph").queryParam("graph", dataset.graphName).queryParam("format", "g6").request().get(String.class);
	}

	
	/**
	 * Test method for {@link de.tud.plt.r43ples.webservice.Misc#getContentOfGraph(java.lang.String, java.lang.String, java.lang.String)}.
	 */
	@Test
	public final void testGetContentOfGraph() {
		String result = target("contentOfGraph").queryParam("graph", dataset.graphName).request().get(String.class);
		Assert.assertThat(result, containsString("type"));
		
		result = target("contentOfGraph").queryParam("graph", dataset.graphName).queryParam("format", "text/turtle").request().get(String.class);
		Assert.assertThat(result, containsString("<http://"));
	}
	
	
	

}