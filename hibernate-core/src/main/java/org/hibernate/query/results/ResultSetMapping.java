/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.hibernate.Incubating;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.named.NamedResultSetMappingMemento;
import org.hibernate.query.results.dynamic.DynamicFetchBuilderLegacy;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMappingProducer;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMappingProducerProvider;

/**
 * Acts as the {@link JdbcValuesMappingProducer} for {@link NativeQuery}
 * or {@link org.hibernate.procedure.ProcedureCall} / {@link jakarta.persistence.StoredProcedureQuery}
 * instances.  These mappings can be defined<ul>
 *     <li>
 *         statically via {@link jakarta.persistence.SqlResultSetMapping} or `hbm.xml` mapping
 *     </li>
 *     <li>
 *         dynamically via Hibernate-specific APIs:<ul>
 *             <li>{@link NativeQuery#addScalar}</li>
 *             <li>{@link NativeQuery#addEntity}</li>
 *             <li>{@link NativeQuery#addJoin}</li>
 *             <li>{@link NativeQuery#addFetch}</li>
 *             <li>{@link NativeQuery#addRoot}</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * @author Steve Ebersole
 */
@Incubating
public interface ResultSetMapping extends JdbcValuesMappingProducer {
	/**
	 * An identifier for the mapping
	 */
	String getMappingIdentifier();

	/**
	 * Indicates whether the mapping is dynamic per {@link ResultSetMapping}
	 */
	boolean isDynamic();

	/**
	 * The number of result builders currently associated with this mapping
	 */
	int getNumberOfResultBuilders();

	/**
	 * The result builders currently associated with this mapping
	 */
	List<ResultBuilder> getResultBuilders();

	/**
	 * Visit each result builder
	 */
	void visitResultBuilders(BiConsumer<Integer, ResultBuilder> resultBuilderConsumer);

	/**
	 * Visit the "legacy" fetch builders.
	 * <p/>
	 * Historically these mappings in Hibernate were defined such that results and fetches are
	 * unaware of each other.  So while {@link ResultBuilder} encapsulates the fetches (see
	 * {@link ResultBuilder#visitFetchBuilders}), fetches defined in the legacy way are unassociated
	 * to their "parent".
	 */
	void visitLegacyFetchBuilders(Consumer<DynamicFetchBuilderLegacy> resultBuilderConsumer);

	/**
	 * Add a builder
	 */
	void addResultBuilder(ResultBuilder resultBuilder);

	/**
	 * Add a legacy fetch builder
	 */
	void addLegacyFetchBuilder(DynamicFetchBuilderLegacy fetchBuilder);

	/**
	 * Create a memento from this mapping.
	 */
	NamedResultSetMappingMemento toMemento(String name);

	static ResultSetMapping resolveResultSetMapping(String name, SessionFactoryImplementor sessionFactory) {
		return resolveResultSetMapping( name, false, sessionFactory );
	}

	static ResultSetMapping resolveResultSetMapping(String name, boolean isDynamic, SessionFactoryImplementor sessionFactory) {
		return sessionFactory
				.getServiceRegistry()
				.getService( JdbcValuesMappingProducerProvider.class )
				.buildResultSetMapping( name, isDynamic, sessionFactory );
	}
}
