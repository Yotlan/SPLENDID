/*
 * This file is part of SPLENDID.
 * 
 * SPLENDID is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SPLENDID is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with SPLENDID.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * SPLENDID uses libraries from the OpenRDF Sesame Project licensed 
 * under the Aduna BSD-style license. 
 */
package de.uni_koblenz.west.splendid;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.rdf4j.federated.monitoring.QueryPlanLog;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.DynamicModel;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
//import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.Query;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryInterruptedException;
import org.eclipse.rdf4j.query.QueryLanguage;
//import org.eclipse.rdf4j.query.QueryResult;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
//import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.TupleQueryResultHandlerException;
import org.eclipse.rdf4j.query.explanation.Explanation.Level;
//import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriter;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.config.RepositoryFactory;
import org.eclipse.rdf4j.repository.config.RepositoryImplConfig;
import org.eclipse.rdf4j.repository.config.RepositoryRegistry;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.eclipse.rdf4j.rio.n3.N3Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uni_koblenz.west.splendid.sources.SourceSelectorBase;
import de.uni_koblenz.west.splendid.test.config.ConfigurationException;
import de.uni_koblenz.west.splendid.QueryInfo;

/**
 * Command Line Interface for the SPLENDID federation.
 * 
 * @author goerlitz@uni-koblenz.de
 */
public class SPLENDID {

	private static final Logger LOGGER = LoggerFactory.getLogger(SPLENDID.class);

	private Repository repo;

	public static QueryInfo queryInfo = new QueryInfo();

	public static void main(String[] args) throws IOException {

		if (args.length < 6) {
			System.out.println(
					"USAGE: java SPLENDID <config> <provenance> <timeout> <result> <explain> <stat> <query>\nONLY have theses args: ");
			for (int i = 0; i < args.length; i++) {
				System.out.println("- " + args[i]);
			}
			System.exit(1);
		}

		String configFile = args[0];
		// String provenancetime = args[1];
		// System.out.println(provenancetime);
		String timeout = args[1];
		String resultfile = args[2];
		// System.out.println(resultfile);
		String explanationfile = args[3];
		// System.out.println(explanationfile);
		String statfile = args[4];
		// System.out.println(statfile);
		List<String> queryFiles = Arrays.asList(Arrays.copyOfRange(args, 5, args.length));
		// System.out.println(queryFiles);

		try {
			LOGGER.info("Init SPLENDID...");
			SPLENDID splendid = new SPLENDID(configFile);
			LOGGER.info("Exec SPARQL queries...");
			splendid.execSparqlQueries(queryFiles, Integer.valueOf(timeout), resultfile, explanationfile, statfile);
		} catch (ConfigurationException e) {
			e.printStackTrace();
		}

		// must exit explicitly due to hang-ups of the HTTP connection pool
		System.exit(0);
	}

	/**
	 * Initialize a new repository based on the configuration settings.
	 * 
	 * @param configFile
	 * @throws ConfigurationException
	 */
	public SPLENDID(String configFile) throws ConfigurationException {
		try {
			this.repo = getRepositoryInstance(loadRepositoryConfig(configFile));
			this.repo.init();
		} catch (RepositoryException e) {
			throw new ConfigurationException("error initializing repository: " + e.getMessage());
		}
	}

	/**
	 * Loads SPARQL queries from the supplied files.
	 * 
	 * @param fileNames The names of the query files.
	 * @return The list of SPARQL queries.
	 */
	private List<String> loadSparqlQueries(List<String> fileNames) {

		List<File> fileList = new ArrayList<File>();
		List<String> queries = new ArrayList<String>();

		for (String fileName : fileNames) {

			File file = new File(fileName);

			if (!file.canRead()) {
				LOGGER.warn("cannot read file: " + file);
				continue;
			}

			// handle directory: list all files
			if (file.isDirectory()) {
				for (File f : file.listFiles()) {
					if (f.isFile() && f.canRead()) {
						fileList.add(f);
					} else {
						LOGGER.warn("cannot read file: " + f);
					}
				}
			}
			if (file.isFile()) {
				fileList.add(file);
			}
		}

		// read query string from files
		Collections.sort(fileList);
		for (File query : fileList) {
			StringBuffer buffer;
			try {
				buffer = new StringBuffer();
				BufferedReader r = new BufferedReader(new FileReader(query));
				String input;
				while ((input = r.readLine()) != null) {
					buffer.append(input).append("\n");
				}
				queries.add(buffer.toString());
				// r.close(); //Also throw an IOException
			} catch (FileNotFoundException e) {
				LOGGER.warn("cannot find query file: " + query);
			} catch (IOException e) {
				LOGGER.warn("cannot read query file: " + query);
			}
		}

		return queries;
	}

	/**
	 * Executes the queries from the supplied query files.
	 * 
	 * @param queryFiles A list of files containing the queries.
	 */
	private void execSparqlQueries(List<String> queryFiles, int timeout, String resultfile, String explanationfile,
			String statfile) throws IOException, FileNotFoundException {
		if (queryFiles == null || queryFiles.size() == 0) {
			LOGGER.warn("No query files specified");
		}

		String homeDir = Paths.get(resultfile).getParent().toString();
		String sourceSelectionTimeFile = homeDir + "/source_selection_time.txt";
		String planningTimeFile = homeDir + "/planning_time.txt";
		String askFile = homeDir + "/ask.txt";
		String execTimeFile = homeDir + "/exec_time.txt";
		String errorFile = homeDir + "/error.txt";
		OutputStream out = System.out;

		for (String queryString : loadSparqlQueries(queryFiles)) {
			// LOGGER.info("Executing QUERY:\n" + queryString);
			// LOGGER.info("RESULT:");
			try (RepositoryConnection con = repo.getConnection()) {
				//RepositoryConnection con = repo.getConnection();
				Query query = con.prepareQuery(QueryLanguage.SPARQL, queryString);
				if (query instanceof TupleQuery) {
					// System.out.println("TUPLE QUERY...");
					TupleQuery tupleQuery = (TupleQuery) query;
					tupleQuery.setMaxExecutionTime(timeout);
					// Just to get nbAsk and planningtime

					// Reset nbAsk and planningtime to avoid issue
					queryInfo.nbAskQuery.set(0);
					queryInfo.planningTime = 0;
					queryInfo.sourceSelectionTime = 0;

					long startTime = System.currentTimeMillis();
					TupleQueryResult res = tupleQuery.evaluate();

					// int count=0;
					// System.out.println("DISPLAY TUPLE QUERY RESULT...");
					// while (res.hasNext()) {
					// BindingSet row = res.next();
					// System.out.println(count+": "+ row);
					// count++;
					// }

					// Write source_selection_time.txt
					try (BufferedWriter sourceSelectionTimeWriter = new BufferedWriter(
							new FileWriter(sourceSelectionTimeFile))) {
						sourceSelectionTimeWriter.write(String.valueOf(queryInfo.sourceSelectionTime));
					}

					// Write query_planning_time.txt
					try (BufferedWriter planningTimeWriter = new BufferedWriter(new FileWriter(planningTimeFile))) {
						planningTimeWriter.write(String.valueOf(queryInfo.planningTime));
					}

					// Write ask.txt
					try (BufferedWriter askWriter = new BufferedWriter(new FileWriter(askFile))) {
						askWriter.write(String.valueOf(queryInfo.nbAskQuery.get()));
					}

					// Write query_plan.txt
					try (BufferedWriter queryPlanWriter = new BufferedWriter(new FileWriter(explanationfile))) {
						// queryPlanWriter.write(QueryPlanLog.getQueryPlan());
						queryPlanWriter.write(tupleQuery.toString());
					}

					// Write results.txt
					try (OutputStream resultOutputStream = new FileOutputStream(resultfile)) {
						tupleQuery.evaluate(new SPARQLResultsCSVWriter(resultOutputStream));
						long runTime = System.currentTimeMillis() - startTime;

						try (BufferedWriter execTimeWriter = new BufferedWriter(new FileWriter(execTimeFile))) {
							execTimeWriter.write(String.valueOf(runTime));
						}
					} catch (QueryInterruptedException e) {
						if (e.getMessage().equals("Query evaluation took too long")) {
							try (BufferedWriter exceptionWriter = new BufferedWriter(new FileWriter(errorFile))) {
								exceptionWriter.write("timeout");
							}
						}
					}

				}
				if (query instanceof GraphQuery) {
					GraphQuery graphQuery = (GraphQuery) query;
					graphQuery.evaluate(new N3Writer(System.out));
				}
				if (query instanceof BooleanQuery) {
					BooleanQuery booleanQuery = (BooleanQuery) query;
					System.out.println(booleanQuery.evaluate());
				}
			}
			System.out.println("\n");
		}
	}

	/**
	 * Loads the repository configuration model from the specified configuration
	 * file.
	 * 
	 * @param configFile The file which contains the configuration data.
	 * @return The configuration data model.
	 * @throws ConfigurationException If the configuration data is invalid or
	 *                                incomplete.
	 */
	private Model loadRepositoryConfig(String configFile) throws ConfigurationException {
		File file = new File(configFile);
		// System.out.println(file);
		String baseURI = file.toURI().toString();
		// System.out.println(baseURI);
		RDFFormat format = Rio.getParserFormatForFileName(configFile).get();
		if (format == null)
			throw new ConfigurationException("unknown RDF format of repository config: " + file);

		try {
			Model model = new DynamicModel(new DynamicModelFactory());
			RDFParser parser = Rio.createParser(format);
			parser.setRDFHandler(new StatementCollector(model));
			parser.parse(new FileReader(file), baseURI);
			// System.out.println(parser);
			return model;

		} catch (UnsupportedRDFormatException e) {
			throw new ConfigurationException(
					"cannot load repository config, unsupported RDF format (" + format + "): " + file);
		} catch (RDFParseException e) {
			throw new ConfigurationException(
					"cannot load repository config, RDF parser error: " + e.getMessage() + ": " + file);
		} catch (RDFHandlerException e) {
			throw new ConfigurationException(
					"cannot load repository config, RDF handler error: " + e.getMessage() + ": " + file);
		} catch (IOException e) {
			throw new ConfigurationException("cannot load repository config, IO error: " + e.getMessage());
		}
	}

	/**
	 * Returns a (un-initialized) Repository instance that has been configured
	 * based on the supplied configuration data.
	 * 
	 * @param configuration The repository configuration data.
	 * @return The created (but un-initialized) repository.
	 * @throws ConfigurationException If no repository could be created due to
	 *                                invalid or incomplete configuration data.
	 */
	private Repository getRepositoryInstance(Model configuration) throws ConfigurationException {

		RepositoryConfig repoConfig = null;
		try {

			// read configuration
			repoConfig = RepositoryConfig.create(configuration, null);
			repoConfig.validate();
			RepositoryImplConfig repoImplConfig = repoConfig.getRepositoryImplConfig();

			// initialize repository factory
			RepositoryRegistry registry = RepositoryRegistry.getInstance();
			RepositoryFactory factory = registry.get(repoImplConfig.getType()).get();
			if (factory == null) {
				throw new ConfigurationException("Unsupported repository type: "
						+ repoImplConfig.getType()
						+ " in repository definition (id:" + repoConfig.getID()
						+ ", title:" + repoConfig.getTitle() + ")");
			}

			// create repository
			return factory.getRepository(repoImplConfig);

		} catch (RepositoryConfigException e) {
			String reason = "error creating repository";
			if (repoConfig != null)
				reason += " (id:" + repoConfig.getID() + ", title:" + repoConfig.getTitle() + ")";
			throw new ConfigurationException(reason + ": " + e.getMessage());
		}
	}

}
