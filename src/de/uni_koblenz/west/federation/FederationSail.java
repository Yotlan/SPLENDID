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
package de.uni_koblenz.west.federation;

import java.util.ArrayList;
import java.util.List;

import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.algebra.evaluation.EvaluationStrategy;
import org.openrdf.query.algebra.evaluation.QueryOptimizer;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryException;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailException;
import org.openrdf.sail.config.SailConfigException;
import org.openrdf.sail.federation.Federation;
import org.openrdf.sail.helpers.SailBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uni_koblenz.west.federation.config.InitializedWithSail;
import de.uni_koblenz.west.federation.evaluation.FederationEvalStrategy;
import de.uni_koblenz.west.federation.sources.SourceSelector;
import de.uni_koblenz.west.statistics.VoidStatistics;

/**
 * Wraps multiple data sources (remote repositories) within a single
 * Sail interface. Queries fragments are sent to a selected subsets
 * of the data sources. Join optimization is based on data statistics
 * and result cardinality estimation.<br>
 * 
 * The implementation is adapted from Sesame's {@link Federation} Sail.
 * 
 * @author Olaf Goerlitz
 * @see org.openrdf.sail.federation.Federation
 */
public class FederationSail extends SailBase {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FederationSail.class);
	
	private List<Repository> members;
	private SourceSelector selector;
	private QueryOptimizer optimizer;
	private EvaluationStrategy evalStrategy;

	private boolean initialized = false;
	
	public FederationSail() {
		this.members = new ArrayList<Repository>();
	}
	
	public void addMember(Repository rep) {
		if (rep == null)
			throw new IllegalArgumentException("federation member must not be NULL");
		this.members.add(rep);
	}
	
	// --- GETTER --------------------------------------------------------------
	
	public EvaluationStrategy getEvalStrategy() {
		return this.evalStrategy;
	}
	
	public QueryOptimizer getFederationOptimizer() {
		return this.optimizer;
	}
	
	public List<Repository> getMembers() {
		return this.members;
	}
	
	public SourceSelector getSourceSelector() {
		return this.selector;
	}
	
	// --- SETTER --------------------------------------------------------------

	public void setEvalStrategy(EvaluationStrategy evalStrategy) {
//		if (evalStrategy == null)
//			throw new IllegalArgumentException("evaluation strategy must not be NULL");
		this.evalStrategy = evalStrategy;
	}
	
	public void setFederationOptimizer(QueryOptimizer optimizer) {
		if (optimizer == null)
			throw new IllegalArgumentException("query optimizer must not be NULL");
		this.optimizer = optimizer;
	}
	
	public void setMembers(List<Repository> members) {
		if (members == null)
			throw new IllegalArgumentException("federation members must not be NULL");
		for (Repository rep : members) {
			if (rep == null)
				throw new IllegalArgumentException("federation member must not be NULL");
		}
		this.members = members;
	}

	public void setSourceSelector(SourceSelector selector) {
		if (selector == null)
			throw new IllegalArgumentException("source selector must not be NULL");
		this.selector = selector;
	}
	
	// -------------------------------------------------------------------------
	
	/**
	 * Initializes the Sail with statistics and optimizer settings.
	 * 
	 * @throws StoreException
	 *         If the Sail could not be initialized.
	 */
	@Override
	public void initialize() throws SailException {
		
		if (initialized) {
			LOGGER.info("Federation Sail is already initialized");
			return;
		}
		
		super.initialize();  // only Sesame 2 needs to initialize super class
		
//		if (this.evalStrategy == null)
//			throw new SailException("Sail evaluation strategy has not been initialized");
		
		// initialize all members
		for (Repository rep : this.members) {
			try {
				rep.initialize();
			} catch (RepositoryException e) {
				throw new SailException("can not initialize repository: " + e.getMessage(), e);
			} catch (IllegalStateException e) {
				LOGGER.debug("member repository is already initialized", e);
			}
		}
		
		// initialize statistics and source selector
		VoidStatistics stats = VoidStatistics.getInstance();
		this.selector.setStatistics(stats);
		this.selector.initialize();
		
		// initialize evaluation strategy
		if (this.evalStrategy == null)
			this.evalStrategy = new FederationEvalStrategy(this.vf);
		
		if (this.evalStrategy instanceof InitializedWithSail) {
			try {
				((InitializedWithSail) this.evalStrategy).init(this);
			} catch (SailConfigException e) {
				throw new SailException("initialization of evaluation strategy failed", e);
			}
		}
		
		initialized = true;
	}
	
	/**
	 * Shuts down the Sail.
	 * 
	 * @throws StoreException
	 *         If the Sail encountered an error or unexpected internal state.
	 */
	@Override
	protected void shutDownInternal() throws SailException {
		for (Repository rep : this.members) {
			try {
				rep.shutDown();
			} catch (RepositoryException e) {
				throw new SailException(e);
			}
		}
	}

	/**
	 * Returns a wrapper for the Sail connections of the federation members.
	 * 
	 * TODO: currently opens connections to *all* federation members,
	 *       should better use lazy initialization of the connections.
	 *       
	 * @return the Sail connection wrapper.
	 */
	@Override
	protected SailConnection getConnectionInternal() throws SailException {
		
		if (!this.initialized)
			throw new IllegalStateException("Sail has not been initialized.");
		
		return new FederationSailConnection(this);
	}

	// SESAME 2 ================================================================
	
	private final ValueFactory vf = new ValueFactoryImpl();
	
	@Override
	public ValueFactory getValueFactory() {
		return vf;
	}

	@Override
	public boolean isWritable() throws SailException {
		return false;
	}
	
}