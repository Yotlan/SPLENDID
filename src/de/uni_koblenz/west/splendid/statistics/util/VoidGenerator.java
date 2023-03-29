package de.uni_koblenz.west.splendid.statistics.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;

import de.uni_koblenz.west.splendid.vocabulary.VOID2;

public class VoidGenerator extends AbstractRDFHandler {
	
	private final Map<IRI, Integer> typeCountMap = new HashMap<IRI, Integer>();
	private final Set<IRI> predicates = new HashSet<IRI>();
	private final Set<Resource> distSubject = new HashSet<Resource>();
	private final Set<Value> distObject = new HashSet<Value>();
	
	private IRI lastPredicate = null;
	private long predCount;
	private long tripleCount;
	private long entityCount;
	
	private ValueFactory vf = SimpleValueFactory.getInstance();
	private BNode dataset = vf.createBNode();
	
	private final RDFWriter writer = new CompactBNodeTurtleWriter(System.out);
	
	private final Comparator<Value> VAL_COMP = new Comparator<Value>() {
		@Override public int compare(Value val1, Value val2) {
			return val1.stringValue().compareTo(val2.stringValue());
		}
	};
	
	// ------------------------------------------------------------------------
	
	private void countType(IRI type) {
		Integer count = typeCountMap.get(type);
		if (count == null) {
			typeCountMap.put(type, 1);
		} else {
			typeCountMap.put(type, 1 + count);
		}
	}
	
	/**
	 * Stores types and predicates occurring with the current subject.
	 * 
	 * @param st the Statement to process.
	 */
	private void storeStatement(Statement st) {
		
		IRI predicate = st.getPredicate();
		predCount++;
		
		// check for type statement
		if (RDF.TYPE.equals(predicate)) {
			
			countType((IRI) st.getObject());
			entityCount++;
		}
		
		// store subject and object
		distSubject.add(st.getSubject());
		distObject.add(st.getObject());
		
		lastPredicate = predicate;
	}
	
	/**
	 * Analyzes the last statements (which have the same subject)
	 * and counts the predicates per type.
	 */
	private void processStoredStatements() {
		if (lastPredicate == null)
			return;
		
		predicates.add(lastPredicate);
		
		// TODO: write predicate statistics
//		System.out.println(lastPredicate + " [" + predCount + "], distS: " + distSubject.size() + ", distObj: " + distObject.size());
		writePredicateStatToVoid(lastPredicate, predCount, distSubject.size(), distObject.size());
		
		// clear stored values;
		distSubject.clear();
		distObject.clear();
		predCount = 0;
	}
	
	private void writePredicateStatToVoid(IRI predicate, long pCount, int distS, int distO) {
		BNode propPartition = vf.createBNode();
		Literal count = vf.createLiteral(String.valueOf(pCount));
		Literal distinctS  = vf.createLiteral(String.valueOf(distS));
		Literal distinctO  = vf.createLiteral(String.valueOf(distO));
		try {
			writer.handleStatement(vf.createStatement(dataset, vf.createIRI(VOID2.propertyPartition.toString()), propPartition));
			writer.handleStatement(vf.createStatement(propPartition, vf.createIRI(VOID2.property.toString()), predicate));
			writer.handleStatement(vf.createStatement(propPartition, vf.createIRI(VOID2.triples.toString()), count));
			writer.handleStatement(vf.createStatement(propPartition, vf.createIRI(VOID2.distinctSubjects.toString()), distinctS));
			writer.handleStatement(vf.createStatement(propPartition, vf.createIRI(VOID2.distinctObjects.toString()), distinctO));
		} catch (RDFHandlerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void writeTypeStatToVoid(Value type, long tCount) {
		BNode classPartition = vf.createBNode();
		Literal count = vf.createLiteral(String.valueOf(tCount));
		try {
			writer.handleStatement(vf.createStatement(dataset, vf.createIRI(VOID2.classPartition.toString()), classPartition));
			writer.handleStatement(vf.createStatement(classPartition, vf.createIRI(VOID2.clazz.toString()), type));
			writer.handleStatement(vf.createStatement(classPartition, vf.createIRI(VOID2.entities.toString()), count));
		} catch (RDFHandlerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void writeGeneralStats() {
		try {
			writer.handleStatement(vf.createStatement(dataset, vf.createIRI(VOID2.triples.toString()), vf.createLiteral(String.valueOf(tripleCount))));
			writer.handleStatement(vf.createStatement(dataset, vf.createIRI(VOID2.properties.toString()), vf.createLiteral(String.valueOf(predicates.size()))));
			writer.handleStatement(vf.createStatement(dataset, vf.createIRI(VOID2.classes.toString()), vf.createLiteral(String.valueOf(typeCountMap.size()))));
			writer.handleStatement(vf.createStatement(dataset, vf.createIRI(VOID2.entities.toString()), vf.createLiteral(String.valueOf(entityCount))));
		} catch (RDFHandlerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// ------------------------------------------------------------------------
	
	@Override
	public void startRDF() throws RDFHandlerException {
		super.startRDF();
		
		writer.startRDF();
		
		// following namespaces which will be shortened automatically
		writer.handleNamespace("void", "http://rdfs.org/ns/void#");
		
		// general void information
		writer.handleStatement(vf.createStatement(dataset, RDF.TYPE, vf.createIRI(VOID2.Dataset.toString())));
	}
	
	
	@Override
	public void handleStatement(Statement st) throws RDFHandlerException {
		
		tripleCount++;
		
		// check if current triple has different predicate than the last triple
		if (!st.getPredicate().equals(lastPredicate)) {
			processStoredStatements();
		}
		
		storeStatement(st);
	}
	
	@Override
	public void endRDF() throws RDFHandlerException {
		super.endRDF();

		processStoredStatements();
		
		// write type statistics
		List<IRI> types = new ArrayList<IRI>(typeCountMap.keySet());
		Collections.sort(types, VAL_COMP);
		for (IRI IRI : types) {
			writeTypeStatToVoid(IRI, typeCountMap.get(IRI));
		}

		// TODO: write general statistics
		writeGeneralStats();
		
		writer.endRDF();
	}
	
	// ------------------------------------------------------------------------
	
	public static void main(String[] args) throws Exception{

		// check for file parameter
		if (args.length < 1) {
			String className = VoidGenerator.class.getName();
			System.err.println("USAGE: java " + className + " RDF.nt{.zip}");
			System.exit(1);
		}
		
		// process all files given as parameters
		for (String arg : args) {

			// check if file exists
			File file = new File(arg);
			if (!file.exists()) {
				System.err.println("file not found: " + file);
				System.exit(1);
			}

			// check if file is not a directory
			if (!file.isFile()) {
				System.err.println("not a normal file: " + file);
				System.exit(1);
			}
			
			processFile(file);
		}
	}
	
	public static void processFile(File file) throws IOException {
		
		// check for gzip file
		if (file.getName().toLowerCase().contains(".gz")) {
			processInputStream(new GZIPInputStream(new FileInputStream(file)), file.getName());
		}
		
		// check for zip file
		else if (file.getName().toLowerCase().contains(".zip")) {
			ZipFile zf = new ZipFile(file);
			if (zf.size() > 1) {
				System.err.println("found multiple files in archive, processing only first one.");
			}
			ZipEntry entry = zf.entries().nextElement();
			if (entry.isDirectory()) {
				System.err.println("found directory instead of normal file in archive: " + entry.getName());
				System.exit(1);
			}
			
			processInputStream(zf.getInputStream(entry), entry.getName());

			try {
				zf.close();
			} catch (Exception e) {
				System.out.println("Can't close ZipFile: "+zf.getName());
			}
		} 
		
		// process data stream of file
		else {
			processInputStream(new FileInputStream(file), file.getName());
		}
	}
	
	public static void processInputStream(InputStream input, String filename) throws IOException {
		
		long start = System.currentTimeMillis();
		System.err.println("processing " + filename);
		
		// identify parser format
		RDFFormat format = Rio.getParserFormatForFileName(filename).get();
		if (format == null) {
			System.err.println("can not identify RDF format for: " + filename);
			System.exit(1);
		}
		
		// initalize parser
		VoidGenerator handler = new VoidGenerator();
		RDFParser parser = Rio.createParser(format);
//		parser.setVerifyData(false);
		//parser.setStopAtFirstError(false);
		parser.setRDFHandler(handler);
		
		try {
			parser.parse(input, "");
		} catch (RDFParseException e) {
			System.err.println("encountered error while parsing " + filename + ": " + e.getMessage());
			System.exit(1);
		} catch (RDFHandlerException e) {
			System.err.println("encountered error while processing " + filename + ": " + e.getMessage());
			System.exit(1);
		}
		finally {
			input.close();
		}
		
		System.err.println((System.currentTimeMillis() - start)/1000 + " seconds elapsed");
	}

}
