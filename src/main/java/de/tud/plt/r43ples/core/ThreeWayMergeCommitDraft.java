package de.tud.plt.r43ples.core;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.util.FileUtils;
import de.tud.plt.r43ples.exception.InternalErrorException;
import de.tud.plt.r43ples.existentobjects.*;
import de.tud.plt.r43ples.iohelper.JenaModelManagement;
import de.tud.plt.r43ples.management.Config;
import de.tud.plt.r43ples.management.RevisionManagementOriginal;
import de.tud.plt.r43ples.mergingUI.MergeQueryTypeEnum;
import de.tud.plt.r43ples.mergingUI.SDDTripleStateEnum;
import de.tud.plt.r43ples.triplestoreInterface.TripleStoreInterfaceSingleton;
import org.apache.log4j.Logger;

import java.util.Iterator;

/**
 * Collection of information for creating a new three way merge commit.
 *
 * @author Stephan Hensel
 */
public class ThreeWayMergeCommitDraft extends MergeCommitDraft {

    /** The logger. **/
    private Logger logger = Logger.getLogger(ThreeWayMergeCommitDraft.class);


    /**
     * The constructor.
     * Creates a three way merge commit draft by using the corresponding meta information.
     *
     * @param graphName the graph name
     * @param branchNameFrom the branch name (from)
     * @param branchNameInto the branch name (into)
     * @param user the user
     * @param message the message
     * @param sdd the SDD URI to use
     * @param triples the triples of the query WITH part
     * @param type the query type (FORCE, AUTO, MANUAL)
     * @param with states if the WITH part is available
     * @throws InternalErrorException
     */
    protected ThreeWayMergeCommitDraft(String graphName, String branchNameFrom, String branchNameInto, String user, String message, String sdd, String triples, MergeTypes type, boolean with) throws InternalErrorException {
        super(graphName, branchNameFrom, branchNameInto, user, message, sdd, MergeActions.MERGE, triples, type, with);
    }

    /**
     * Tries to create a new commit draft as a new commit in the triple store.
     * If possible it will create the corresponding revision and the meta data.
     *
     * @return the commit (has attribute which indicates if the commit was executed or not)
     */
    protected ThreeWayMergeCommit createCommitInTripleStore() throws InternalErrorException {

        String revisionGraphURI = getRevisionGraph().getRevisionGraphUri();
        String revisionUriFrom = getRevisionGraph().getRevisionUri(getBranchNameFrom());
        String revisionUriInto = getRevisionGraph().getRevisionUri(getBranchNameInto());

        // Differ between MERGE query with specified SDD and without SDD
        String usedSDDURI = getRevisionGraph().getSDD(getSdd());

        // Get the common revision with shortest path
        Revision commonRevision = this.getPathCalculationInterface().getCommonRevisionWithShortestPath(getRevisionGraph(), new Revision(getRevisionGraph(), revisionUriFrom, false), new Revision(getRevisionGraph(), revisionUriInto, false));

        // Create the revision progress for from and into
        String namedGraphUriFrom = getRevisionManagement().getTemporaryRevisionProgressFromURI(getRevisionGraph());
        String namedGraphUriInto = getRevisionManagement().getTemporaryRevisionProgressIntoURI(getRevisionGraph());
        String namedGraphUriDiff = getRevisionManagement().getTemporaryDifferenceModelURI(getRevisionGraph());
        String uriA = "http://eatld.et.tu-dresden.de/branch-from";
        String uriB = "http://eatld.et.tu-dresden.de/branch-into";

        Revision fromRevision = new Revision(getRevisionGraph(), revisionUriFrom, false);
        Revision intoRevision = new Revision(getRevisionGraph(), revisionUriInto, false);

        createRevisionProgresses(revisionGraphURI, getGraphName(),
                this.getPathCalculationInterface().getPathBetweenStartAndTargetRevision(getRevisionGraph(), commonRevision, fromRevision),
                namedGraphUriFrom, uriA,
                this.getPathCalculationInterface().getPathBetweenStartAndTargetRevision(getRevisionGraph(), commonRevision, intoRevision),
                namedGraphUriInto, uriB, commonRevision);

        // Create difference model
        createDifferenceTripleModel(getGraphName(), namedGraphUriDiff, namedGraphUriFrom, uriA, namedGraphUriInto, uriB,
                usedSDDURI);

        // The created revision
        Revision revision;

        // Differ between the different merge queries
        if ((getType() != null) && (getType().equals(MergeTypes.AUTO)) && !isWith()) {
            logger.debug("AUTO MERGE query detected");
            // Create the merged revision
            revision = createMergedRevision(namedGraphUriDiff, MergeQueryTypeEnum.AUTO);
            return addMetaInformation(revision, namedGraphUriDiff, commonRevision, fromRevision, intoRevision);
        } else if ((getType() != null) && (getType().equals(MergeTypes.MANUAL)) && isWith()) {
            logger.debug("MANUAL MERGE query detected");
            // Create the merged revision
            revision = createMergedRevision(namedGraphUriDiff, MergeQueryTypeEnum.MANUAL);
            return addMetaInformation(revision, namedGraphUriDiff, commonRevision, fromRevision, intoRevision);
        } else if ((getType() == null) && isWith()) {
            logger.debug("MERGE WITH query detected");
            // Create the merged revision
            revision = createMergedRevision(namedGraphUriDiff, MergeQueryTypeEnum.WITH);
            return addMetaInformation(revision, namedGraphUriDiff, commonRevision, fromRevision, intoRevision);
        } else if ((getType() == null) && !isWith()) {
            logger.debug("MERGE query detected");
            // Check if difference model contains conflicts
            String queryASK = String.format("ASK { %n" + "	GRAPH <%s> { %n"
                    + " 	?ref <http://eatld.et.tu-dresden.de/sddo#isConflicting> \"true\"^^<http://www.w3.org/2001/XMLSchema#boolean> . %n"
                    + "	} %n" + "}", namedGraphUriDiff);
            if (getTripleStoreInterface().executeAskQuery(queryASK)) {
                // Difference model contains conflicts
                // Return the conflict model to the client
                String conflictModel = RevisionManagementOriginal.getContentOfGraph(namedGraphUriDiff, "text/turtle");
                return new ThreeWayMergeCommit(getRevisionGraph(), null,null, null, null, fromRevision, null, intoRevision, null, null, null, true, conflictModel, namedGraphUriDiff);
            } else {
                // Difference model contains no conflicts
                // Create the merged revision
                revision = createMergedRevision(namedGraphUriDiff, MergeQueryTypeEnum.COMMON);
                return addMetaInformation(revision, namedGraphUriDiff, commonRevision, fromRevision, intoRevision);
            }
        } else {
            throw new InternalErrorException("This is not a valid MERGE query");
        }
    }

    /**
     * Adds meta information for commit and revision to the revision graph.
     *
     * <img src="{@docRoot}../../doc/revision management description/r43ples-threewaymerge.png" />
     *
     * @param generatedRevision the generated revision
     * @param namedGraphUriDiff the named graph URI of the difference model
     * @param commonRevision the common revision of this merge
     * @param usedSourceRevision the used source revision (from)
     * @param usedTargetRevision the used target revision (into)
     * @return the created commit
     * @throws InternalErrorException
     */
    private ThreeWayMergeCommit addMetaInformation(Revision generatedRevision, String namedGraphUriDiff, Revision commonRevision, Revision usedSourceRevision, Revision usedTargetRevision) throws InternalErrorException {

        String commitURI = getRevisionManagement().getNewThreeWayMergeCommitURI(getRevisionGraph(), generatedRevision.getRevisionIdentifier());;

        Branch usedSourceBranch = getRevisionGraph().getBranch(getBranchNameFrom(), true);
        Branch usedTargetBranch = getRevisionGraph().getBranch(getBranchNameInto(), true);

        String personUri = RevisionManagementOriginal.getUserURI(getUser());

        // Create a new commit (activity)
        StringBuilder queryContent = new StringBuilder(1000);
        queryContent.append(String.format(
                "<%s> a rmo:ThreeWayMergeCommit, rmo:MergeCommit, rmo:Commit; "
                        + "	prov:wasAssociatedWith <%s> ;"
                        + "	dc-terms:title \"%s\" ;"
                        + "	prov:atTime \"%s\"^^xsd:dateTime ; %n"
                        + " prov:generated <%s> ;"
                        + " rmo:usedSourceRevision <%s> ;"
                        + " rmo:usedSourceBranch <%s> ;"
                        + " rmo:usedTargetRevision <%s> ;"
                        + " rmo:usedTargetBranch <%s> .",
                commitURI, personUri, getMessage(), getTimeStamp(),
                generatedRevision.getRevisionURI(), usedSourceRevision.getRevisionURI(), usedSourceBranch.getReferenceURI(),
                usedTargetRevision.getRevisionURI(), usedTargetBranch.getReferenceURI()));

        // Create revision meta data
        queryContent.append(String.format(
                "<%s> a rmo:Revision ; %n"
                        + "	rmo:addSet <%s> ; %n"
                        + "	rmo:deleteSet <%s> ; %n"
                        + "	rmo:revisionNumber \"%s\" ; %n"
                        + "	rmo:belongsTo <%s> ; %n"
                        + " prov:wasDerivedFrom <%s>, <%s> . %n",
                generatedRevision.getRevisionURI(), generatedRevision.getAddSetURI(), generatedRevision.getDeleteSetURI(), generatedRevision.getRevisionIdentifier(),
                usedTargetBranch.getReferenceURI(), usedSourceRevision.getRevisionURI(), usedTargetRevision.getRevisionURI()));

        String query = Config.prefixes
                + String.format("INSERT DATA { GRAPH <%s> { %s } }", getRevisionGraph().getRevisionGraphUri(),
                queryContent.toString());

        getTripleStoreInterface().executeUpdateQuery(query);

        // Move branch to new revision
        moveBranchReference(getRevisionGraph().getRevisionGraphUri(), usedTargetBranch.getReferenceURI(), usedTargetRevision.getRevisionURI(), generatedRevision.getRevisionURI());
        // Update the target branch object
        usedTargetBranch = getRevisionGraph().getBranch(getBranchNameInto(), true);

        return new ThreeWayMergeCommit(getRevisionGraph(), commitURI, getUser(), getTimeStamp(), getMessage(),
                usedSourceRevision, usedSourceBranch, usedTargetRevision, usedTargetBranch, generatedRevision,
                commonRevision, false, null, namedGraphUriDiff);
    }

    /**
     * Create a merged revision with meta data
     *
     * @param graphNameDifferenceTripleModel the graph name of the difference triple model
     * @param type the merge query type
     * @return the created revision
     * @throws InternalErrorException
     */
    private Revision createMergedRevision(String graphNameDifferenceTripleModel, MergeQueryTypeEnum type) throws InternalErrorException {

        // Create an empty temporary graph which will contain the merged full content
        String graphNameOfMerged = getRevisionManagement().getTemporaryMergedURI(getRevisionGraph());
        getTripleStoreInterface().executeUpdateQuery(String.format("DROP SILENT GRAPH <%s>", graphNameOfMerged));
        getTripleStoreInterface().executeCreateGraph(graphNameOfMerged);

        // Get the full graph name of branch A
        String graphNameOfBranchA = getRevisionGraph().getReferenceGraph(getBranchNameFrom());
        // Get the full graph name of branch B
        String graphNameOfBranchB = getRevisionGraph().getReferenceGraph(getBranchNameInto());

        if (type.equals(MergeQueryTypeEnum.MANUAL)) {
            // Manual merge query
            RevisionManagementOriginal.executeINSERT(graphNameOfMerged, getTriples());
        } else {
            // Copy graph B to temporary merged graph
            String queryCopy = String.format("COPY <%s> TO <%s>", graphNameOfBranchB, graphNameOfMerged);
            getTripleStoreInterface().executeUpdateQuery(queryCopy);

            // Get the triples from branch A which should be added to/removed from the merged revision
            String triplesToAdd = "";
            String triplesToDelete = "";

            // Get all difference groups
            String queryDifferenceGroup = Config.prefixes + String.format(
                    "SELECT ?differenceCombinationURI ?automaticResolutionState ?tripleStateA ?tripleStateB ?conflict %n"
                            + "WHERE { GRAPH <%s> { %n"
                            + "	?differenceCombinationURI a rpo:DifferenceGroup ; %n"
                            + "		sddo:automaticResolutionState ?automaticResolutionState ; %n"
                            + "		sddo:hasTripleStateA ?tripleStateA ; %n"
                            + "		sddo:hasTripleStateB ?tripleStateB ; %n"
                            + "		sddo:isConflicting ?conflict . %n"
                            + "} }", graphNameDifferenceTripleModel);

            // Iterate over all difference groups
            ResultSet resultSetDifferenceGroups = getTripleStoreInterface().executeSelectQuery(queryDifferenceGroup);
            while (resultSetDifferenceGroups.hasNext()) {
                QuerySolution qsCurrentDifferenceGroup = resultSetDifferenceGroups.next();

                String currentDifferencGroupURI = qsCurrentDifferenceGroup.getResource("?differenceCombinationURI").toString();
                String currentDifferencGroupAutomaticResolutionState = qsCurrentDifferenceGroup.getResource("?automaticResolutionState").toString();
//				Currently not needed
//				String currentDifferencGroupTripleStateA = qsCurrentDifferenceGroup.getResource("?tripleStateA").toString();
//				String currentDifferencGroupTripleStateB = qsCurrentDifferenceGroup.getResource("?tripleStateB").toString();
                boolean currentDifferencGroupConflict = qsCurrentDifferenceGroup.getLiteral("?conflict").getBoolean();

                // Get all differences (triples) of current difference group
                String queryDifference = Config.prefixes + String.format(
                        "SELECT ?s ?p ?o %n"
                                + "WHERE { GRAPH <%s> { %n"
                                + "	<%s> a rpo:DifferenceGroup ; %n"
                                + "		rpo:hasDifference ?blankDifference . %n"
                                + "	?blankDifference a rpo:Difference ; %n"
                                + "		rpo:hasTriple ?triple . %n"
                                + "	?triple rdf:subject ?s . %n"
                                + "	?triple rdf:predicate ?p . %n"
                                + "	?triple rdf:object ?o . %n"
                                + "} }", graphNameDifferenceTripleModel, currentDifferencGroupURI);

                // Iterate over all differences (triples)
                ResultSet resultSetDifferences = getTripleStoreInterface().executeSelectQuery(queryDifference);
                while (resultSetDifferences.hasNext()) {
                    QuerySolution qsCurrentDifference = resultSetDifferences.next();

                    String subject = "<" + qsCurrentDifference.getResource("?s").toString() + ">";
                    String predicate = "<" + qsCurrentDifference.getResource("?p").toString() + ">";

                    // Differ between literal and resource
                    String object;
                    if (qsCurrentDifference.get("?o").isLiteral()) {
                        object = "\"" + qsCurrentDifference.getLiteral("?o").toString() + "\"";
                    } else {
                        object = "<" + qsCurrentDifference.getResource("?o").toString() + ">";
                    }

                    if (	type.equals(MergeQueryTypeEnum.AUTO) ||
                            type.equals(MergeQueryTypeEnum.COMMON) ||
                            (type.equals(MergeQueryTypeEnum.WITH) && !currentDifferencGroupConflict) ) {
                        // MERGE AUTO or common MERGE query
                        if (currentDifferencGroupAutomaticResolutionState.equals(SDDTripleStateEnum.ADDED.getSddRepresentation())) {
                            // Triple should be added
                            triplesToAdd += subject + " " + predicate + " " + object + " . \n";
                        } else {
                            // Triple should be deleted
                            triplesToDelete += subject + " " + predicate + " " + object + " . \n";
                        }
                    } else {
                        // MERGE WITH query - conflicting triple
                        Model model = JenaModelManagement.readNTripleStringToJenaModel(getTriples());
                        // Create ASK query which will check if the model contains the specified triple
                        String queryAsk = String.format(
                                "ASK { %n"
                                        + " %s %s %s %n"
                                        + "}", subject, predicate, object);
                        Query query = QueryFactory.create(queryAsk);
                        QueryExecution qe = QueryExecutionFactory.create(query, model);
                        boolean resultAsk = qe.execAsk();
                        qe.close();
                        model.close();
                        if (resultAsk) {
                            // Model contains the specified triple
                            // Triple should be added
                            triplesToAdd += subject + " " + predicate + " " + object + " . \n";
                        } else {
                            // Triple should be deleted
                            triplesToDelete += subject + " " + predicate + " " + object + " . \n";
                        }
                    }
                }
                // Update the merged graph
                // Insert triplesToAdd
                RevisionManagementOriginal.executeINSERT(graphNameOfMerged, triplesToAdd);
                // Delete triplesToDelete
                RevisionManagementOriginal.executeDELETE(graphNameOfMerged, triplesToDelete);
            }
        }

        // Calculate the add and delete sets

        // Get all added triples (concatenate all triples which are in MERGED but not in A and all triples which are in MERGED but not in B)
        String queryAddedTriples = String.format(
                "CONSTRUCT {?s ?p ?o} %n"
                        + "WHERE { %n"
                        + "	GRAPH <%s> { ?s ?p ?o } %n"
                        + "	FILTER NOT EXISTS { "
                        + "		GRAPH <%s> { ?s ?p ?o } %n"
                        + "	} %n"
                        + "}", graphNameOfMerged, graphNameOfBranchA);

        String addedTriples = getTripleStoreInterface().executeConstructQuery(queryAddedTriples, FileUtils.langNTriple);

        queryAddedTriples = String.format(
                "CONSTRUCT {?s ?p ?o} %n"
                        + "WHERE { %n"
                        + "	GRAPH <%s> { ?s ?p ?o } %n"
                        + "	FILTER NOT EXISTS { %n"
                        + "		GRAPH <%s> { ?s ?p ?o } %n"
                        + "	} %n"
                        + "}", graphNameOfMerged, graphNameOfBranchB);

        addedTriples += getTripleStoreInterface().executeConstructQuery(queryAddedTriples, FileUtils.langNTriple);

        // Get all removed triples (concatenate all triples which are in A but not in MERGED and all triples which are in B but not in MERGED)
        String queryRemovedTriples = String.format(
                "CONSTRUCT {?s ?p ?o} %n"
                        + "WHERE { %n"
                        + "	GRAPH <%s> { ?s ?p ?o } %n"
                        + "	FILTER NOT EXISTS { %n"
                        + "		GRAPH <%s> { ?s ?p ?o } %n"
                        + "	} %n"
                        + "}", graphNameOfBranchA, graphNameOfMerged);

        String deletedTriples = getTripleStoreInterface().executeConstructQuery(queryRemovedTriples, FileUtils.langNTriple);

        queryRemovedTriples = String.format(
                "CONSTRUCT {?s ?p ?o} %n"
                        + "WHERE { %n"
                        + "	GRAPH <%s> { ?s ?p ?o } %n"
                        + "	FILTER NOT EXISTS { %n"
                        + "		GRAPH <%s> { ?s ?p ?o } %n"
                        + "	} %n"
                        + "}", graphNameOfBranchB, graphNameOfMerged);

        deletedTriples += getTripleStoreInterface().executeConstructQuery(queryRemovedTriples, FileUtils.langNTriple);

        // Creates a new revision draft an creates a corresponding revision - no meta data will be written
        RevisionDraft revisionDraft = new RevisionDraft(getRevisionManagement(), getRevisionGraph(), getBranchNameInto(), addedTriples, deletedTriples);

        return revisionDraft.createRevisionInTripleStore();
    }

    /**
     * Create the revision progresses for both branches.
     *
     * @param pathFrom the path with all revisions from start revision to target revision of the from branch
     * @param graphNameRevisionProgressFrom the graph name of the revision progress of the from branch
     * @param uriFrom the URI of the revision progress of the from branch
     * @param pathInto the linked list with all revisions from start revision to target revision of the into branch
     * @param graphNameRevisionProgressInto the graph name of the revision progress of the into branch
     * @param uriInto the URI of the revision progress of the into branch
     * @throws InternalErrorException
     */
    protected void createRevisionProgresses(final String revisionGraph, final String graphName,
                                            Path pathFrom, String graphNameRevisionProgressFrom, String uriFrom,
                                            Path pathInto, String graphNameRevisionProgressInto, String uriInto, Revision commonRevision) throws InternalErrorException {
        logger.info("Create the revision progress of branch from and into.");

        RevisionGraph graph = new RevisionGraph(graphName);

        if (!((pathFrom.getRevisionPath().size() > 0) && (pathInto.getRevisionPath().size() > 0))) {
            throw new InternalErrorException("Revision path contains no revisions.");
        }

        // Get the full graph name of common revision or create full revision graph of common revision
        String fullGraphNameCommonRevision;
        Boolean tempGraphWasCreated = false;
        try {
            fullGraphNameCommonRevision = graph.getReferenceGraph(commonRevision.getRevisionIdentifier());
        } catch (InternalErrorException e) {
            // Create a temporary full graph
            // TODO move to new RevisionManagement
            fullGraphNameCommonRevision = graphName + "RM-TEMP-REVISION-PROGRESS-FULLGRAPH";
            RevisionManagementOriginal.generateFullGraphOfRevision(graphName, commonRevision.getRevisionIdentifier(), fullGraphNameCommonRevision);
            tempGraphWasCreated = true;
        }

        // Create revision progress of branch from
        createRevisionProgress(revisionGraph, pathFrom, fullGraphNameCommonRevision, graphNameRevisionProgressFrom, uriFrom);

        // Create revision progress of branch into
        createRevisionProgress(revisionGraph, pathInto, fullGraphNameCommonRevision, graphNameRevisionProgressInto, uriInto);

        // Drop the temporary full graph
        if (tempGraphWasCreated) {
            logger.info("Drop the temporary full graph.");
            TripleStoreInterfaceSingleton.get().executeUpdateQuery("DROP SILENT GRAPH <" + fullGraphNameCommonRevision + ">");
        }

    }

    /**
     * Create the revision progress.
     *
     * @param path the path with all revisions from start revision to target revision
     * @param fullGraphNameCommonRevision the full graph name of the common revision (first revision of path)
     * @param graphNameRevisionProgress the graph name of the revision progress
     * @param uri the URI of the revision progress
     * @throws InternalErrorException
     */
    private void createRevisionProgress(final String revisionGraph, Path path, String fullGraphNameCommonRevision, String graphNameRevisionProgress, String uri) throws InternalErrorException {
        logger.info("Create the revision progress of " + uri + " in graph " + graphNameRevisionProgress + ".");

        TripleStoreInterfaceSingleton.get().executeUpdateQuery(String.format("DROP SILENT GRAPH <%s>", graphNameRevisionProgress));
        TripleStoreInterfaceSingleton.get().executeUpdateQuery(String.format("CREATE GRAPH  <%s>", graphNameRevisionProgress));
        Iterator<Revision> iteList = path.getRevisionPath().iterator();

        if (iteList.hasNext()) {
            String firstRevision = iteList.next().getRevisionURI();

            // Create the initial content
            logger.info("Create the initial content.");
            String queryInitial = Config.prefixes + String.format(
                    "INSERT { GRAPH <%s> { %n"
                            + "	<%s> a rpo:RevisionProgress; %n"
                            + "		rpo:original [ %n"
                            + "			rdf:subject ?s ; %n"
                            + "			rdf:predicate ?p ; %n"
                            + "			rdf:object ?o ; %n"
                            + "			rmo:references <%s> %n"
                            + "		] %n"
                            + "} } WHERE { %n"
                            + "	GRAPH <%s> %n"
                            + "		{ ?s ?p ?o . } %n"
                            + "}", graphNameRevisionProgress, uri, firstRevision, fullGraphNameCommonRevision);

            // Execute the query which generates the initial content
            TripleStoreInterfaceSingleton.get().executeUpdateQuery(queryInitial);

            // Update content by current add and delete set - remove old entries
            while (iteList.hasNext()) {
                String revision = iteList.next().getRevisionURI();
                logger.info("Update content by current add and delete set of revision " + revision + " - remove old entries.");
                // Get the ADD and DELETE set URIs
                String addSetURI = RevisionManagementOriginal.getAddSetURI(revision, revisionGraph);
                String deleteSetURI = RevisionManagementOriginal.getDeleteSetURI(revision, revisionGraph);

                if ((addSetURI != null) && (deleteSetURI != null)) {

                    // Update the revision progress with the data of the current revision ADD set

                    // Delete old entries (original)
                    String queryRevision = Config.prefixes + String.format(
                            "DELETE { GRAPH <%s> { %n"
                                    + "	<%s> rpo:original ?blank . %n"
                                    + "	?blank rdf:subject ?s . %n"
                                    + "	?blank rdf:predicate ?p . %n"
                                    + "	?blank rdf:object ?o . %n"
                                    + "	?blank rmo:references ?revision . %n"
                                    + "} } %n"
                                    + "WHERE { "
                                    + "		GRAPH <%s> { %n"
                                    + "			<%s> rpo:original ?blank . %n"
                                    + "			?blank rdf:subject ?s . %n"
                                    + "			?blank rdf:predicate ?p . %n"
                                    + "			?blank rdf:object ?o . %n"
                                    + "			?blank rmo:references ?revision . %n"
                                    + "		} %n"
                                    + "		GRAPH <%s> { %n"
                                    + "			?s ?p ?o %n"
                                    + "		} %n"
                                    + "};", graphNameRevisionProgress, uri, graphNameRevisionProgress, uri, addSetURI);

                    queryRevision += "\n";

                    // Delete old entries (added)
                    queryRevision += String.format(
                            "DELETE { GRAPH <%s> { %n"
                                    + "	<%s> rpo:added ?blank . %n"
                                    + "	?blank rdf:subject ?s . %n"
                                    + "	?blank rdf:predicate ?p . %n"
                                    + "	?blank rdf:object ?o . %n"
                                    + "	?blank rmo:references ?revision . %n"
                                    + "} } %n"
                                    + "WHERE { "
                                    + "		GRAPH <%s> { %n"
                                    + "			<%s> rpo:added ?blank . %n"
                                    + "			?blank rdf:subject ?s . %n"
                                    + "			?blank rdf:predicate ?p . %n"
                                    + "			?blank rdf:object ?o . %n"
                                    + "			?blank rmo:references ?revision . %n"
                                    + "		} %n"
                                    + "		GRAPH <%s> { %n"
                                    + "			?s ?p ?o %n"
                                    + "		} %n"
                                    + "};", graphNameRevisionProgress, uri, graphNameRevisionProgress, uri, addSetURI);

                    queryRevision += "\n";

                    // Delete old entries (removed)
                    queryRevision += String.format(
                            "DELETE { GRAPH <%s> { %n"
                                    + "	<%s> rpo:removed ?blank . %n"
                                    + "	?blank rdf:subject ?s . %n"
                                    + "	?blank rdf:predicate ?p . %n"
                                    + "	?blank rdf:object ?o . %n"
                                    + "	?blank rmo:references ?revision . %n"
                                    + "} } %n"
                                    + "WHERE { "
                                    + "		GRAPH <%s> { %n"
                                    + "			<%s> rpo:removed ?blank . %n"
                                    + "			?blank rdf:subject ?s . %n"
                                    + "			?blank rdf:predicate ?p . %n"
                                    + "			?blank rdf:object ?o . %n"
                                    + "			?blank rmo:references ?revision . %n"
                                    + "		} %n"
                                    + "		GRAPH <%s> { %n"
                                    + "			?s ?p ?o %n"
                                    + "		} %n"
                                    + "};", graphNameRevisionProgress, uri, graphNameRevisionProgress, uri, addSetURI);

                    queryRevision += "\n";

                    // Insert new entries (added)
                    queryRevision += String.format(
                            "INSERT { GRAPH <%s> {%n"
                                    + "	<%s> a rpo:RevisionProgress; %n"
                                    + "		rpo:added [ %n"
                                    + "			rdf:subject ?s ; %n"
                                    + "			rdf:predicate ?p ; %n"
                                    + "			rdf:object ?o ; %n"
                                    + "			rmo:references <%s> %n"
                                    + "		] %n"
                                    + "} } WHERE { %n"
                                    + "	GRAPH <%s> %n"
                                    + "		{ ?s ?p ?o . } %n"
                                    + "};", graphNameRevisionProgress, uri, revision, addSetURI);

                    queryRevision += "\n \n";

                    // Update the revision progress with the data of the current revision DELETE set

                    // Delete old entries (original)
                    queryRevision += String.format(
                            "DELETE { GRAPH <%s> { %n"
                                    + "	<%s> rpo:original ?blank . %n"
                                    + "	?blank rdf:subject ?s . %n"
                                    + "	?blank rdf:predicate ?p . %n"
                                    + "	?blank rdf:object ?o . %n"
                                    + "	?blank rmo:references ?revision . %n"
                                    + "} } %n"
                                    + "WHERE { "
                                    + "		GRAPH <%s> { %n"
                                    + "			<%s> rpo:original ?blank . %n"
                                    + "			?blank rdf:subject ?s . %n"
                                    + "			?blank rdf:predicate ?p . %n"
                                    + "			?blank rdf:object ?o . %n"
                                    + "			?blank rmo:references ?revision . %n"
                                    + "		} %n"
                                    + "		GRAPH <%s> { %n"
                                    + "			?s ?p ?o %n"
                                    + "		} %n"
                                    + "};", graphNameRevisionProgress, uri, graphNameRevisionProgress, uri, deleteSetURI);

                    queryRevision += "\n";

                    // Delete old entries (added)
                    queryRevision += String.format(
                            "DELETE { GRAPH <%s> { %n"
                                    + "	<%s> rpo:added ?blank . %n"
                                    + "	?blank rdf:subject ?s . %n"
                                    + "	?blank rdf:predicate ?p . %n"
                                    + "	?blank rdf:object ?o . %n"
                                    + "	?blank rmo:references ?revision . %n"
                                    + "} } %n"
                                    + "WHERE { "
                                    + "		GRAPH <%s> { %n"
                                    + "			<%s> rpo:added ?blank . %n"
                                    + "			?blank rdf:subject ?s . %n"
                                    + "			?blank rdf:predicate ?p . %n"
                                    + "			?blank rdf:object ?o . %n"
                                    + "			?blank rmo:references ?revision . %n"
                                    + "		} %n"
                                    + "		GRAPH <%s> { %n"
                                    + "			?s ?p ?o %n"
                                    + "		} %n"
                                    + "};", graphNameRevisionProgress, uri, graphNameRevisionProgress, uri, deleteSetURI);

                    queryRevision += "\n";

                    // Delete old entries (removed)
                    queryRevision += String.format(
                            "DELETE { GRAPH <%s> { %n"
                                    + "	<%s> rpo:removed ?blank . %n"
                                    + "	?blank rdf:subject ?s . %n"
                                    + "	?blank rdf:predicate ?p . %n"
                                    + "	?blank rdf:object ?o . %n"
                                    + "	?blank rmo:references ?revision . %n"
                                    + "} } %n"
                                    + "WHERE { "
                                    + "		GRAPH <%s> { %n"
                                    + "			<%s> rpo:removed ?blank . %n"
                                    + "			?blank rdf:subject ?s . %n"
                                    + "			?blank rdf:predicate ?p . %n"
                                    + "			?blank rdf:object ?o . %n"
                                    + "			?blank rmo:references ?revision . %n"
                                    + "		} %n"
                                    + "		GRAPH <%s> { %n"
                                    + "			?s ?p ?o %n"
                                    + "		} %n"
                                    + "};", graphNameRevisionProgress, uri, graphNameRevisionProgress, uri, deleteSetURI);

                    queryRevision += "\n";

                    // Insert new entries (removed)
                    queryRevision += String.format(
                            "INSERT { GRAPH <%s> { %n"
                                    + "	<%s> a rpo:RevisionProgress; %n"
                                    + "		rpo:removed [ %n"
                                    + "			rdf:subject ?s ; %n"
                                    + "			rdf:predicate ?p ; %n"
                                    + "			rdf:object ?o ; %n"
                                    + "			rmo:references <%s> %n"
                                    + "		] %n"
                                    + "} } WHERE { %n"
                                    + "	GRAPH <%s> %n"
                                    + "		{ ?s ?p ?o . } %n"
                                    + "}", graphNameRevisionProgress, uri, revision, deleteSetURI);

                    // Execute the query which updates the revision progress by the current revision
                    TripleStoreInterfaceSingleton.get().executeUpdateQuery(queryRevision);

                } else {
                    //TODO Error management - is needed when a ADD or DELETE set is not referenced in the current implementation this error should not occur
                    logger.error("ADD or DELETE set of " + revision + "does not exists.");
                }
                logger.info("Revision progress was created.");
            }
        }
    }

    /**
     * Create the difference triple model which contains all differing triples.
     *
     * @param graphName the graph name
     * @param graphNameDifferenceTripleModel the graph name of the difference triple model
     * @param graphNameRevisionProgressA the graph name of the revision progress of branch A
     * @param uriA the URI of the revision progress of branch A
     * @param graphNameRevisionProgressB the graph name of the revision progress of branch B
     * @param uriB the URI of the revision progress of branch B
     * @param uriSDD the URI of the SDD to use
     */
    private void createDifferenceTripleModel(String graphName, String graphNameDifferenceTripleModel, String graphNameRevisionProgressA, String uriA, String graphNameRevisionProgressB, String uriB, String uriSDD){

        logger.info("Create the difference triple model");
        TripleStoreInterfaceSingleton.get().executeUpdateQuery(String.format("DROP SILENT GRAPH <%s>", graphNameDifferenceTripleModel));
        TripleStoreInterfaceSingleton.get().executeUpdateQuery(String.format("CREATE GRAPH  <%s>", graphNameDifferenceTripleModel));

        // Templates for revision A and B
        String sparqlTemplateRevisionA = String.format(
                "	GRAPH <%s> { %n"
                        + "		<%s> <%s> ?blankA . %n"
                        + "			?blankA rdf:subject ?s . %n"
                        + "			?blankA rdf:predicate ?p . %n"
                        + "			?blankA rdf:object ?o . %n"
                        + "			?blankA rmo:references ?revisionA . %n"
                        + "	} %n", graphNameRevisionProgressA, uriA, "%s");
        String sparqlTemplateRevisionB = String.format(
                "	GRAPH <%s> { %n"
                        + "		<%s> <%s> ?blankB . %n"
                        + "			?blankB rdf:subject ?s . %n"
                        + "			?blankB rdf:predicate ?p . %n"
                        + "			?blankB rdf:object ?o . %n"
                        + "			?blankB rmo:references ?revisionB . %n"
                        + "	} %n", graphNameRevisionProgressB, uriB, "%s");

        String sparqlTemplateNotExistsRevisionA = String.format(
                "FILTER NOT EXISTS { %n"
                        + "	GRAPH <%s> { %n"
                        + "		<%s> ?everything ?blankA . %n"
                        + "			?blankA rdf:subject ?s . %n"
                        + "			?blankA rdf:predicate ?p . %n"
                        + "			?blankA rdf:object ?o . %n"
                        + "			?blankA rmo:references ?revisionA . %n"
                        + "	} %n"
                        + "}", graphNameRevisionProgressA, uriA);

        String sparqlTemplateNotExistsRevisionB = String.format(
                "FILTER NOT EXISTS { %n"
                        + "	GRAPH <%s> { %n"
                        + "		<%s> ?everything ?blankB . %n"
                        + "			?blankB rdf:subject ?s . %n"
                        + "			?blankB rdf:predicate ?p . %n"
                        + "			?blankB rdf:object ?o . %n"
                        + "			?blankB rmo:references ?revisionB . %n"
                        + "	} %n"
                        + "}", graphNameRevisionProgressB, uriB);

        // Get all structural definitions which are generating differences
        String queryDifferingSD = String.format(
                "PREFIX sddo: <http://eatld.et.tu-dresden.de/sddo#> %n"
                        + "PREFIX sdd:  <http://eatld.et.tu-dresden.de/sdd#> %n"
                        + "PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#> %n"
                        + "SELECT ?combinationURI ?tripleStateA ?tripleStateB ?conflict ?automaticResolutionState %n"
                        + "WHERE { GRAPH <%s> { %n"
                        + "	<%s> a sddo:StructuralDefinitionGroup ;"
                        + "		sddo:hasStructuralDefinition ?combinationURI ."
                        + "	?combinationURI a sddo:StructuralDefinition ; %n"
                        + "		sddo:hasTripleStateA ?tripleStateA ; %n"
                        + "		sddo:hasTripleStateB ?tripleStateB ; %n"
                        + "		sddo:isConflicting ?conflict ; %n"
                        + "		sddo:automaticResolutionState ?automaticResolutionState . %n"
                        + "} } %n", Config.sdd_graph, uriSDD);

        // Iterate over all differing combination URIs
        ResultSet resultSetDifferences = TripleStoreInterfaceSingleton.get().executeSelectQuery(queryDifferingSD);
        while (resultSetDifferences.hasNext()) {
            QuerySolution qs = resultSetDifferences.next();

            String currentDifferenceCombinationURI = qs.getResource("?combinationURI").toString();
            String currentTripleStateA = qs.getResource("?tripleStateA").toString();
            String currentTripleStateB = qs.getResource("?tripleStateB").toString();
            // Will return an integer value because virtuoso stores boolean internal as integer
            String currentConflictState = qs.getLiteral("?conflict").toString();
            // TDB returns boolean value without "" -> add it to use it in the next query correctly
            if (currentConflictState.equals("true^^http://www.w3.org/2001/XMLSchema#boolean")) {
                currentConflictState = "\"true\"^^<http://www.w3.org/2001/XMLSchema#boolean>";
            } else {
                currentConflictState = "\"false\"^^<http://www.w3.org/2001/XMLSchema#boolean>";
            }
            String currentAutomaticResolutionState = qs.getResource("?automaticResolutionState").toString();

            String querySelectPart = "SELECT ?s ?p ?o %s %s %n";
            String sparqlQueryRevisionA = null;
            String sparqlQueryRevisionB = null;

            // A
            if (currentTripleStateA.equals(SDDTripleStateEnum.ADDED.getSddRepresentation())) {
                // In revision A the triple was added
                querySelectPart = String.format(querySelectPart, "?revisionA", "%s");
                sparqlQueryRevisionA = String.format(sparqlTemplateRevisionA, SDDTripleStateEnum.ADDED.getRpoRepresentation());
            } else if (currentTripleStateA.equals(SDDTripleStateEnum.DELETED.getSddRepresentation())) {
                // In revision A the triple was deleted
                querySelectPart = String.format(querySelectPart, "?revisionA", "%s");
                sparqlQueryRevisionA = String.format(sparqlTemplateRevisionA, SDDTripleStateEnum.DELETED.getRpoRepresentation());
            } else if (currentTripleStateA.equals(SDDTripleStateEnum.ORIGINAL.getSddRepresentation())) {
                // In revision A the triple is original
                querySelectPart = String.format(querySelectPart, "?revisionA", "%s");
                sparqlQueryRevisionA = String.format(sparqlTemplateRevisionA, SDDTripleStateEnum.ORIGINAL.getRpoRepresentation());
            } else if (currentTripleStateA.equals(SDDTripleStateEnum.NOTINCLUDED.getSddRepresentation())) {
                // In revision A the triple is not included
                querySelectPart = String.format(querySelectPart, "", "%s");
                sparqlQueryRevisionA = sparqlTemplateNotExistsRevisionA;
            }

            // B
            if (currentTripleStateB.equals(SDDTripleStateEnum.ADDED.getSddRepresentation())) {
                // In revision B the triple was added
                querySelectPart = String.format(querySelectPart, "?revisionB");
                sparqlQueryRevisionB = String.format(sparqlTemplateRevisionB, SDDTripleStateEnum.ADDED.getRpoRepresentation());
            } else if (currentTripleStateB.equals(SDDTripleStateEnum.DELETED.getSddRepresentation())) {
                // In revision B the triple was deleted
                querySelectPart = String.format(querySelectPart, "?revisionB");
                sparqlQueryRevisionB = String.format(sparqlTemplateRevisionB, SDDTripleStateEnum.DELETED.getRpoRepresentation());
            } else if (currentTripleStateB.equals(SDDTripleStateEnum.ORIGINAL.getSddRepresentation())) {
                // In revision B the triple is original
                querySelectPart = String.format(querySelectPart, "?revisionB");
                sparqlQueryRevisionB = String.format(sparqlTemplateRevisionB, SDDTripleStateEnum.ORIGINAL.getRpoRepresentation());
            } else if (currentTripleStateB.equals(SDDTripleStateEnum.NOTINCLUDED.getSddRepresentation())) {
                // In revision B the triple is not included
                querySelectPart = String.format(querySelectPart, "");
                sparqlQueryRevisionB = sparqlTemplateNotExistsRevisionB;
            }

            // Concatenated SPARQL query
            String query = String.format(
                    Config.prefixes
                            + "%s"
                            + "WHERE { %n"
                            + "%s"
                            + "%s"
                            + "} %n", querySelectPart, sparqlQueryRevisionA, sparqlQueryRevisionB);

            // Iterate over all triples
            ResultSet resultSetTriples = TripleStoreInterfaceSingleton.get().executeSelectQuery(query);
            while (resultSetTriples.hasNext()) {
                QuerySolution qsQuery = resultSetTriples.next();

                String subject = qsQuery.getResource("?s").toString();
                String predicate = qsQuery.getResource("?p").toString();

                // Differ between literal and resource
                String object = "";
                if (qsQuery.get("?o").isLiteral()) {
                    object = "\"" + qsQuery.getLiteral("?o").toString() + "\"";
                } else {
                    object = "<" + qsQuery.getResource("?o").toString() + ">";
                }

                // Create the references A and B part of the query
                String referencesAB = ". %n";
                if (!currentTripleStateA.equals(SDDTripleStateEnum.NOTINCLUDED.getSddRepresentation()) && !currentTripleStateB.equals(SDDTripleStateEnum.NOTINCLUDED.getSddRepresentation())) {
                    referencesAB = String.format(
                            "			rpo:referencesA <%s> ; %n"
                                    + "			rpo:referencesB <%s> %n", qsQuery.getResource("?revisionA").toString(),
                            qsQuery.getResource("?revisionB").toString());
                } else if (currentTripleStateA.equals(SDDTripleStateEnum.NOTINCLUDED.getSddRepresentation()) && !currentTripleStateB.equals(SDDTripleStateEnum.NOTINCLUDED.getSddRepresentation())) {
                    referencesAB = String.format(
                            "			rpo:referencesB <%s> %n", qsQuery.getResource("?revisionB").toString());
                } else if (!currentTripleStateA.equals(SDDTripleStateEnum.NOTINCLUDED.getSddRepresentation()) && currentTripleStateB.equals(SDDTripleStateEnum.NOTINCLUDED.getSddRepresentation())) {
                    referencesAB = String.format(
                            "			rpo:referencesA <%s> %n", qsQuery.getResource("?revisionA").toString());
                }

                String queryTriple = Config.prefixes + String.format(
                        "INSERT DATA { GRAPH <%s> {%n"
                                + "	<%s> a rpo:DifferenceGroup ; %n"
                                + "	sddo:hasTripleStateA <%s> ; %n"
                                + "	sddo:hasTripleStateB <%s> ; %n"
                                + "	sddo:isConflicting %s ; %n"
                                + "	sddo:automaticResolutionState <%s> ; %n"
                                + "	rpo:hasDifference [ %n"
                                + "		a rpo:Difference ; %n"
                                + "			rpo:hasTriple [ %n"
                                + "				rdf:subject <%s> ; %n"
                                + "				rdf:predicate <%s> ; %n"
                                + "				rdf:object %s %n"
                                + "			] ; %n"
                                + "%s"
                                + "	] . %n"
                                + "} }", graphNameDifferenceTripleModel,
                        currentDifferenceCombinationURI,
                        currentTripleStateA,
                        currentTripleStateB,
                        currentConflictState,
                        currentAutomaticResolutionState,
                        subject,
                        predicate,
                        object,
                        referencesAB);

                TripleStoreInterfaceSingleton.get().executeUpdateQuery(queryTriple);
            }
        }
    }

}