package de.tud.plt.r43ples;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.rdf.model.Model;

import de.tud.plt.r43ples.iohelper.JenaModelManagement;
import de.tud.plt.r43ples.webservice.Endpoint;

/**
 * This class provides the basic methods for test execution.
 * Each test has to inherit from it.
 * 
 * @author Stephan Hensel
 * 
 */
public class R43plesTest {

	/** The logger. **/
	private static Logger logger = Logger.getLogger(R43plesTest.class);
	/** The endpoint. **/
	protected final Endpoint ep = new Endpoint();
		
		
	/**
	 * Checks the isomorphism between two sets of triples.
	 * 
	 * @param result_set the result set
	 * @param result_set_format the result set format
	 * @param expected_set the expected set
	 * @param expected_set_format the expected set format
	 * @return true if the two models are isomorphic
	 */
	public boolean check_isomorphism(String result_set, String result_set_format, String expected_set, String expected_set_format) {
		return check_isomorphism(JenaModelManagement.readStringToJenaModel(result_set, result_set_format), JenaModelManagement.readStringToJenaModel(expected_set, expected_set_format));
	}
	
		
	/**
	 * Checks the isomorphism between two models.
	 * 
	 * @param result_model the result model
	 * @param expected_model the expected model
	 * @return true if the two models are isomorphic
	 */
	public boolean check_isomorphism(Model result_model, Model expected_model) {
		// Check isomorphism
		if (result_model.isIsomorphicWith(expected_model)) {
			return true;
		} else {
			// Get the differences between the models
			
			// Resulting model contains all statements which are in the result_model but not in the expected_model		
			logger.error("The following statements are in the result_model but not in the expected_model: \n" 
					+ JenaModelManagement.convertJenaModelToNTriple(result_model.difference(expected_model)));
			// Resulting model contains all statements which are in the expected_model but not in the result_model	
			logger.error("The following statements are in the expected_model but not in the result_model: \n" 
					+ JenaModelManagement.convertJenaModelToNTriple(expected_model.difference(result_model)));
			
			return false;
		}
	}
	
	
	
	
	
}
