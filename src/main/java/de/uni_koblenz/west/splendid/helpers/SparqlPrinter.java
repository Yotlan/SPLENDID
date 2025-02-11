/*
 * This file is part of RDF Federator.
 * Copyright 2010 Olaf Goerlitz
 * 
 * RDF Federator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * RDF Federator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with RDF Federator.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * RDF Federator uses libraries from the OpenRDF Sesame Project licensed 
 * under the Aduna BSD-style license. 
 */
package de.uni_koblenz.west.splendid.helpers;

//import org.eclipse.rdf4j.query.algebra.NaryTupleOperator;
//import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.algebra.BinaryTupleOperator;
import org.eclipse.rdf4j.query.algebra.Compare;
import org.eclipse.rdf4j.query.algebra.Filter;
import org.eclipse.rdf4j.query.algebra.FunctionCall;
import org.eclipse.rdf4j.query.algebra.Join;
import org.eclipse.rdf4j.query.algebra.QueryModelNode;
import org.eclipse.rdf4j.query.algebra.Regex;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.Str;
import org.eclipse.rdf4j.query.algebra.ValueConstant;
import org.eclipse.rdf4j.query.algebra.Var;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor;

import de.uni_koblenz.west.splendid.model.MappedStatementPattern;
import de.uni_koblenz.west.splendid.model.RemoteQuery;

/**
 * Generates the SPARQL representation for a query model.
 * 
 * TODO: need to extend beyond join and triple patterns.
 * 
 * @author Olaf Goerlitz.
 */
public class SparqlPrinter extends AbstractQueryModelVisitor<RuntimeException> {
	
	private static final SparqlPrinter printer = new SparqlPrinter();
	
	private StringBuffer buffer = new StringBuffer();
	private String indent = "  ";
	private boolean secondFilterParam = false;
	private boolean regexFilter = false;
	
	/**
	 * Prints the SPARQL query starting with the given query model node.
	 *  
	 * @param root the root node of the query model to print.
	 * @return the SPARQL representation of the query model.
	 */
	public static String print(QueryModelNode root) {
		synchronized (printer) {
			printer.buffer.setLength(0);
			root.visit(printer);
			return printer.buffer.toString();
		}
	}

	// --------------------------------------------------------------
	
	@Override
//	public void meetNaryTupleOperator(NaryTupleOperator node) throws RuntimeException {
	protected void meetBinaryTupleOperator(BinaryTupleOperator node) throws RuntimeException {
		if (node instanceof Join) {
			// Sesame 3.0:
//			for (TupleExpr expr : node.getArgs()) {
//				expr.visit(this);
//			}
			// Sesame 2.3.2:
			node.getLeftArg().visit(this);
			node.getRightArg().visit(this);
			
		} else {
			throw new UnsupportedOperationException("not yet implemented");
		}
	}
	
	@Override
	public void meet(Filter node) throws RuntimeException {
		// print affected expressions first
//		for (TupleExpr expr : node.getArgs()) {
//			expr.visit(this);
//		}

		node.getArg().visit(this);
			
		// then the applied filters conditions
		buffer.append(indent);
		if (node.getCondition().toString().contains("Regex")) {
			regexFilter = true;
			buffer.append("FILTER REGEX (");
			node.getCondition().visit(this);
			buffer.append(")");
			secondFilterParam = false;
			regexFilter = false;
		} else {
			regexFilter = false;
			buffer.append("FILTER (");
			node.getCondition().visit(this);
			buffer.append(")\n");
		}
	}
	
	@Override
	public void meet(Compare node) throws RuntimeException {
		node.getLeftArg().visit(this);
		buffer.append(" ").append(node.getOperator().getSymbol()).append(" ");
		node.getRightArg().visit(this);
	}
	
	@Override
	public void meet(Var node) throws RuntimeException {
		if (regexFilter) {
			//System.out.println(node.getParentNode().getSignature());
			boolean isApplyFunction = false;
			QueryModelNode applyNode = null;
			boolean isCastStr = false;
			QueryModelNode isInRegexFilter = node;
			while(!(isInRegexFilter.getParentNode() instanceof MappedStatementPattern) && !(isInRegexFilter.getParentNode() instanceof Regex)) {
				//System.out.println(isInRegexFilter.getSignature());
				if (isInRegexFilter.getParentNode() instanceof FunctionCall) {
					isApplyFunction = true;
					applyNode = isInRegexFilter.getParentNode();
				}
				if (isInRegexFilter.getParentNode() instanceof Str) {
					isCastStr = true;
				}
				isInRegexFilter = isInRegexFilter.getParentNode();
			}
			//System.out.println(isInRegexFilter.getParentNode().getSignature());
			if (isApplyFunction) {
				if (applyNode.getSignature().contains("lower-case")) {
					buffer.append("LCASE(");
				} else if (applyNode.getSignature().contains("upper-case")) {
					buffer.append("UCASE(");
				} else if (applyNode.getSignature().contains("string-length")) {
					buffer.append("STRLEN(");
				} else if (applyNode.getSignature().contains("encode-for-uri")) {
					buffer.append("ENCODE_FOR_URI(");
				} else {
					throw new RuntimeException("Not handle the following apply function: "+applyNode.getSignature());
				}
			}
			if (isCastStr) {
				buffer.append("STR(");
			}
			if (node.hasValue()) {
				// bound variable (constant)
				Value value = node.getValue();
				if (value instanceof IRI)
					buffer.append("<").append(value).append(">");
				else
					buffer.append(value);
			} else {
				// unbound variable
				if (node.isAnonymous())
					buffer.append("[]");
				else
					buffer.append("?").append(node.getName());
			}
			if (isCastStr) {
				buffer.append(")");
			}
			if (isApplyFunction) {
				buffer.append(")");
			}
			if (isInRegexFilter.getParentNode().getSignature().contains("Regex") && !secondFilterParam) {
				buffer.append(",");
				secondFilterParam = true;
			}
		} else {
			if (node.hasValue()) {
				// bound variable (constant)
				Value value = node.getValue();
				if (value instanceof IRI)
					buffer.append("<").append(value).append(">");
				else
					buffer.append(value);
			} else {
				// unbound variable
				if (node.isAnonymous())
					buffer.append("[]");
				else
					buffer.append("?").append(node.getName());
			}
		}
	}
	
	@Override
	public void meet(ValueConstant node) throws RuntimeException {
		if (regexFilter) {
			//System.out.println(node.getParentNode().getSignature());
			QueryModelNode isInRegexFilter = node;
			while(!(isInRegexFilter.getParentNode() instanceof MappedStatementPattern) && !(isInRegexFilter.getParentNode() instanceof Regex)) {
				//System.out.println(isInRegexFilter.getSignature());
				isInRegexFilter = isInRegexFilter.getParentNode();
			}
			Value value = node.getValue();
			if (value instanceof IRI)
				buffer.append("<").append(value).append(">");
			else
				buffer.append(value);
			if (isInRegexFilter.getParentNode().getSignature().contains("Regex") && !secondFilterParam) {
				buffer.append(",");
				secondFilterParam = true;
			}
		} else {
			Value value = node.getValue();
			if (value instanceof IRI)
				buffer.append("<").append(value).append(">");
			else
				buffer.append(value);
		}
	}

	@Override
	public void meet(StatementPattern node) throws RuntimeException {
		buffer.append(indent);
		for (Var var : node.getVarList()) {
			var.visit(this);
			buffer.append(" ");
		}
		buffer.append(".\n");
	}

}
