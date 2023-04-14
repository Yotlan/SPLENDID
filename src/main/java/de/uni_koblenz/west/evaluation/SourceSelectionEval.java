package de.uni_koblenz.west.evaluation;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.io.BufferedWriter;
import java.io.FileWriter;

import org.apache.log4j.BasicConfigurator;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.helpers.StatementPatternCollector;
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uni_koblenz.west.splendid.FederationSail;
import de.uni_koblenz.west.splendid.index.Graph;
import de.uni_koblenz.west.splendid.model.MappedStatementPattern;
import de.uni_koblenz.west.splendid.model.SubQueryBuilder;
import de.uni_koblenz.west.splendid.optimizer.AbstractFederationOptimizer;
import de.uni_koblenz.west.splendid.sources.SourceSelector;
import de.uni_koblenz.west.splendid.test.config.Configuration;
import de.uni_koblenz.west.splendid.test.config.ConfigurationException;
import de.uni_koblenz.west.splendid.test.config.Query;

/**
 * Evaluation of the source selection.
 * 
 * @author goerlitz@uni-koblenz.de
 */
public class SourceSelectionEval {

	private static final Logger LOGGER = LoggerFactory.getLogger(SourceSelectionEval.class);

	private static final String CONFIG_FILE = "config.properties";

	private SourceSelector finder;
	private SubQueryBuilder queryBuilder;
	private Iterator<Query> queries;
	private PrintStream output;

	public SourceSelectionEval(Configuration config) throws ConfigurationException {
		FederationSail fedSail = config.getFederationSail();
		this.finder = fedSail.getSourceSelector();
		this.queryBuilder = ((AbstractFederationOptimizer) fedSail.getFederationOptimizer()).getBuilder();
		this.queries = config.getQueryIterator();
		this.output = config.getResultStream();
	}

	public void testQueries(Configuration config, String provenanceFile) {

		// table header
		try {
			// System.out.println("QUERIES: "+ this.queries.hasNext());
			try (BufferedWriter provenanceWriter = new BufferedWriter(new FileWriter(provenanceFile))) {
				provenanceWriter.write("query;triples;#sources;sources\n");
				long startTime = System.currentTimeMillis();

				while (this.queries.hasNext()) {
					Query query = this.queries.next();
					SPARQLParser parser = new SPARQLParser();
					TupleExpr expr;
					try {
						expr = parser.parseQuery(query.getQuery(), null).getTupleExpr();
						// LOGGER.info(expr.toString());
					} catch (MalformedQueryException e) {
						LOGGER.error("cannot parse Query " + query.getName() + ": " + e.getMessage());
						continue;
					}

					// group all triple patterns by assigned data source
					List<MappedStatementPattern> mappedPatterns = finder
							.mapSources(StatementPatternCollector.process(expr), config);
					List<List<MappedStatementPattern>> patterns = this.queryBuilder.getGroups(mappedPatterns);

					Set<Graph> selectedSources = new HashSet<Graph>();
					// int queriesToSend = 0;
					// int patternToSend = 0;
					for (List<MappedStatementPattern> pList : patterns) {
						// int patternCount = pList.size();
						Set<Graph> sourceSet = pList.get(0).getSources();
						selectedSources.addAll(sourceSet);
						// queriesToSend += sourceSet.size();
						// patternToSend += sourceSet.size() * patternCount;
						provenanceWriter.write(query.getName() + ";" + pList + ";" + pList.get(0).getSources().size() + ";"
								+ pList.get(0).getSources().toString() + "\n");
					}
				}
				long runTime = System.currentTimeMillis() - startTime;
				System.out.println(runTime);
			}

			// output.close();
		} catch (Exception e) {
			System.err.println(e);
		}
	}

	public static void main(String[] args) {

		BasicConfigurator.configure();

		// check arguments for name of configuration file
		String configFile;
		if (args.length < 2) {
			LOGGER.info("no config file specified; using default: " + CONFIG_FILE);
			configFile = CONFIG_FILE;
		} else {
			configFile = args[0];
		}
		String provenanceFile = args[1];

		try {
			Configuration config = Configuration.load(configFile);
			SourceSelectionEval eval = new SourceSelectionEval(config);
			// System.out.println(configFile + "|" + provenanceFile);
			eval.testQueries(config, provenanceFile);
		} catch (IOException e) {
			LOGGER.error("cannot load test config: " + e.getMessage());
		} catch (ConfigurationException e) {
			LOGGER.error("cannot init configuration: " + e.getMessage());
		}

	}

}
