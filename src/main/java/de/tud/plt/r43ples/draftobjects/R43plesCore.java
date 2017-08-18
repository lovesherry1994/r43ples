package de.tud.plt.r43ples.draftobjects;

import de.tud.plt.r43ples.exception.InternalErrorException;
import de.tud.plt.r43ples.existentobjects.ThreeWayMergeCommit;
import de.tud.plt.r43ples.existentobjects.UpdateCommit;
import de.tud.plt.r43ples.management.R43plesRequest;
import org.apache.log4j.Logger;

import java.util.ArrayList;

/**
 * This class implements the R43ples core interface and provides methods to access the core functions of R43ples.
 *
 * @author Stephan Hensel
 *
 */
public class R43plesCore implements R43plesCoreInterface {

    /** The logger. **/
    private Logger logger = Logger.getLogger(R43plesCore.class);


    /**
     * The constructor.
     */
    protected R43plesCore() {

    }

    /**
     * Create a new update commit.
     *
     * @param request the request received by R43ples
     * @return the list of created update commits
     * @throws InternalErrorException
     */
    @Override
    public ArrayList<UpdateCommit> createUpdateCommit(R43plesRequest request) throws InternalErrorException {
        UpdateCommitDraft updateCommitDraft = new UpdateCommitDraft(request);
        return updateCommitDraft.createCommitInTripleStore();
    }

    /**
     * Create a new update commit.
     *
     * @param graphName the graph name
     * @param addSet the add set as N-Triples
     * @param deleteSet the delete set as N-Triples
     * @param user the user
     * @param message the message
     * @param derivedFromIdentifier the revision identifier of the revision or the reference identifier from which the new revision should be derive from
     * @return the list of created update commits
     * @throws InternalErrorException
     */
    @Override
    public ArrayList<UpdateCommit> createUpdateCommit(String graphName, String addSet, String deleteSet, String user, String message, String derivedFromIdentifier) throws InternalErrorException {
        UpdateCommitDraft updateCommitDraft = new UpdateCommitDraft(graphName, addSet, deleteSet, user, message, derivedFromIdentifier);
        return updateCommitDraft.createCommitInTripleStore();
    }





    @Override
    public ThreeWayMergeCommit createThreeWayMergeCommit(R43plesRequest request) throws InternalErrorException {
        return null;
    }

    @Override
    public ThreeWayMergeCommit createThreeWayMergeCommit(String graphName, String addSet, String deleteSet, String user, String message, String derivedFromIdentifierSource, String derivedFromIdentifierTarget) throws InternalErrorException {
        return null;
    }

}