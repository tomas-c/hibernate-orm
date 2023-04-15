/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.graph;

import org.hibernate.Incubating;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.sql.results.graph.embeddable.EmbeddableInitializer;
import org.hibernate.sql.results.graph.entity.EntityInitializer;
import org.hibernate.sql.results.jdbc.spi.RowProcessingState;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.exec.spi.ExecutionContext;

/**
 * Defines a multi-step process for initializing entity, collection and
 * composite state.  Each step is performed on each initializer
 * before starting the next step.
 *
 * @author Steve Ebersole
 */
@Incubating
public interface Initializer {
	NavigablePath getNavigablePath();

	ModelPart getInitializedPart();

	Object getInitializedInstance();

	/**
	 * Step 1 - Resolve the key value for this initializer for the current
	 * row.
	 *
	 * After this point, the initializer knows the entity/collection/component
	 * key for the current row
	 */
	void resolveKey(RowProcessingState rowProcessingState);

	/**
	 * Step 2 - Using the key resolved in {@link #resolveKey}, resolve the
	 * instance (of the thing initialized) to use for the current row.
	 *
	 * After this point, the initializer knows the entity/collection/component
	 * instance for the current row based on the resolved key
	 *
	 * todo (6.0) : much of the various implementations of this are similar enough to handle in a common base implementation (templating?)
	 * 		things like resolving as managed (Session cache), from second-level cache, from LoadContext, etc..
	 */
	void resolveInstance(RowProcessingState rowProcessingState);

	/**
	 * Step 3 - Initialize the state of the instance resolved in
	 * {@link #resolveInstance} from the current row values.
	 *
	 * All resolved state for the current row is injected into the resolved
	 * instance
	 */
	void initializeInstance(RowProcessingState rowProcessingState);

	/**
	 * Lifecycle method called at the end of the current row processing.
	 * Provides ability to complete processing from the current row and
	 * prepare for the next row.
	 */
	void finishUpRow(RowProcessingState rowProcessingState);

	/**
	 * Lifecycle method called at the very end of the result values processing
	 */
	default void endLoading(ExecutionContext context) {
		// by default - nothing to do
	}

	default boolean isAttributeAssignableToConcreteDescriptor(
			FetchParentAccess parentAccess,
			AttributeMapping referencedModelPart) {
		final EntityInitializer entityInitializer = parentAccess == null ?
				null :
				parentAccess.findFirstEntityInitializer();
		if ( entityInitializer != null ) {
			final EntityPersister concreteDescriptor = entityInitializer.getConcreteDescriptor();
			if ( concreteDescriptor.getEntityMetamodel().isPolymorphic() ) {
				final EntityPersister declaringType = (EntityPersister) referencedModelPart.getDeclaringType()
						.findContainingEntityMapping();
				if ( concreteDescriptor != declaringType ) {
					return declaringType.getSubclassEntityNames().contains( concreteDescriptor.getEntityName() );
				}
			}
		}
		return true;
	}

	default boolean isEmbeddableInitializer(){
		return false;
	}

	default boolean isEntityInitializer(){
		return false;
	}

	default boolean isCollectionInitializer(){
		return false;
	}

	/**
	 * A utility method to avoid casting explicitly to EntityInitializer
	 *
	 * @return EntityInitializer if this is an instance of EntityInitializer otherwise {@code null}
	 */
	default EntityInitializer asEntityInitializer() {
		return null;
	}

	/**
	 * A utility method to avoid casting explicitly to EmbeddableInitializer
	 *
	 * @return EmbeddableInitializer if this is an instance of EmbeddableInitializer otherwise {@code null}
	 */
	default EmbeddableInitializer asEmbeddableInitializer() {
		return null;
	}

}
