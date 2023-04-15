/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.graph.embeddable;

import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.internal.StandardEmbeddableInstantiator;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.CompositeIdentifierMapping;
import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.VirtualModelPart;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;
import org.hibernate.metamodel.spi.EmbeddableRepresentationStrategy;
import org.hibernate.metamodel.spi.ValueAccess;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.results.graph.AbstractFetchParentAccess;
import org.hibernate.sql.results.graph.AssemblerCreationState;
import org.hibernate.sql.results.graph.DomainResultAssembler;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParentAccess;
import org.hibernate.sql.results.graph.Fetchable;
import org.hibernate.sql.results.graph.Initializer;
import org.hibernate.sql.results.graph.collection.CollectionInitializer;
import org.hibernate.sql.results.graph.entity.EntityInitializer;
import org.hibernate.sql.results.internal.NullValueAssembler;
import org.hibernate.sql.results.jdbc.spi.RowProcessingState;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.hibernate.internal.util.collections.CollectionHelper.arrayList;
import static org.hibernate.sql.results.graph.entity.internal.BatchEntityInsideEmbeddableSelectFetchInitializer.BATCH_PROPERTY;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractEmbeddableInitializer extends AbstractFetchParentAccess implements EmbeddableInitializer,
		ValueAccess {
	private static final Object NULL_MARKER = new Object() {
		@Override
		public String toString() {
			return "Composite NULL_MARKER";
		}
	};

	private final NavigablePath navigablePath;
	private final EmbeddableValuedModelPart embedded;
	private final EmbeddableMappingType representationEmbeddable;
	private final EmbeddableRepresentationStrategy representationStrategy;
	private final FetchParentAccess fetchParentAccess;
	private final boolean createEmptyCompositesEnabled;
	private final SessionFactoryImplementor sessionFactory;

	private final List<DomainResultAssembler<?>> assemblers;

	private final boolean usesStandardInstantiation;

	// per-row state
	private final Object[] rowState;
	private Boolean stateAllNull;
	private Boolean stateInjected;
	protected Object compositeInstance;
	private boolean isParentInitialized;
	private RowProcessingState wrappedProcessingState;

	public AbstractEmbeddableInitializer(
			EmbeddableResultGraphNode resultDescriptor,
			FetchParentAccess fetchParentAccess,
			AssemblerCreationState creationState) {
		this.navigablePath = resultDescriptor.getNavigablePath();
		this.embedded = resultDescriptor.getReferencedMappingContainer();
		this.fetchParentAccess = fetchParentAccess;

		final EmbeddableMappingType embeddableTypeDescriptor = embedded.getEmbeddableTypeDescriptor();
		if ( embedded instanceof CompositeIdentifierMapping ) {
			representationEmbeddable = ( (CompositeIdentifierMapping) embedded ).getMappedIdEmbeddableTypeDescriptor();
		}
		else {
			representationEmbeddable = embeddableTypeDescriptor;
		}

		this.representationStrategy = representationEmbeddable.getRepresentationStrategy();
		this.usesStandardInstantiation = representationStrategy.getInstantiator() instanceof StandardEmbeddableInstantiator;

		final int size = embeddableTypeDescriptor.getNumberOfFetchables();
		this.rowState = new Object[ size ];
		this.assemblers = arrayList( size );

		// We never want to create empty composites for the FK target or PK, otherwise collections would break
		createEmptyCompositesEnabled = !ForeignKeyDescriptor.PART_NAME.equals( navigablePath.getLocalName() )
				&& !ForeignKeyDescriptor.TARGET_PART_NAME.equals( navigablePath.getLocalName() )
				&& !EntityIdentifierMapping.ROLE_LOCAL_NAME.equals( navigablePath.getLocalName() )
				&& embeddableTypeDescriptor.isCreateEmptyCompositesEnabled();

		sessionFactory = creationState.getSqlAstCreationContext().getSessionFactory();
		initializeAssemblers( resultDescriptor, creationState, embeddableTypeDescriptor );
	}

	protected void initializeAssemblers(
			EmbeddableResultGraphNode resultDescriptor,
			AssemblerCreationState creationState,
			EmbeddableMappingType embeddableTypeDescriptor) {
		final int size = embeddableTypeDescriptor.getNumberOfFetchables();
		for ( int i = 0; i < size; i++ ) {
			final Fetchable stateArrayContributor = embeddableTypeDescriptor.getFetchable( i );
			final Fetch fetch = resultDescriptor.findFetch( stateArrayContributor );

			final DomainResultAssembler<?> stateAssembler = fetch == null
					? new NullValueAssembler<>( stateArrayContributor.getJavaType() )
					: fetch.createAssembler( this, creationState );

			assemblers.add( stateAssembler );
		}
	}

	@Override
	public EmbeddableValuedModelPart getInitializedPart() {
		return embedded;
	}

	@Override
	public FetchParentAccess getFetchParentAccess() {
		return fetchParentAccess;
	}

	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public Object getCompositeInstance() {
		return compositeInstance == NULL_MARKER ? null : compositeInstance;
	}

	@Override
	public FetchParentAccess findFirstEntityDescriptorAccess() {
		if ( fetchParentAccess == null ) {
			return null;
		}
		return fetchParentAccess.findFirstEntityDescriptorAccess();
	}

	@Override
	public EntityInitializer findFirstEntityInitializer() {
		final FetchParentAccess firstEntityDescriptorAccess = findFirstEntityDescriptorAccess();
		if ( firstEntityDescriptorAccess == null ) {
			return null;
		}
		return firstEntityDescriptorAccess.findFirstEntityInitializer();
	}

	@Override
	public void resolveKey(RowProcessingState processingState) {
		// nothing to do
	}

	@Override
	public void resolveInstance(RowProcessingState processingState) {
		// nothing to do
	}

	@Override
	public void initializeInstance(RowProcessingState processingState) {
		EmbeddableLoadingLogger.EMBEDDED_LOAD_LOGGER.debugf( "Initializing composite instance [%s]", navigablePath );

		if ( compositeInstance == NULL_MARKER ) {
			// we already know it is null
			return;
		}

		if ( isParentInstanceNull() ) {
			compositeInstance = NULL_MARKER;
			return;
		}

		// IMPORTANT: This method might be called multiple times for the same role for a single row.
		// 		EmbeddableAssembler calls it as part of its `#assemble` and the RowReader calls it
		// 		as part of its normal Initializer handling
		//
		// 		Unfortunately, as currently structured, we need this double call mainly to handle
		// 		the case composite keys, especially those with key-m-1 refs.
		//
		//		When we are processing a non-key embeddable, all initialization happens in
		//		the first call, and we can safely ignore the second call.
		//
		//		When we are processing a composite key, we really need to react to both calls.
		//
		//		Unfortunately, atm, because we reuse `EmbeddedAttributeMapping` in a few of these
		//		foreign-key scenarios, we cannot easily tell when one models a key or not.
		//
		//		While this is "important" to be able to avoid extra handling, it is really only
		//		critical in the case we have custom constructor injection.  Luckily, custom instantiation
		//		is only allowed for non-key usage atm, so we leverage that distinction here

		if ( !usesStandardInstantiation ) {
			// we have a custom instantiator
			if ( compositeInstance != null ) {
				return;
			}
		}
		if ( isParentInitialized ) {
			return;
		}

		// We need to possibly wrap the processing state if the embeddable is within an aggregate
		if ( wrappedProcessingState == null ) {
			wrappedProcessingState = wrapProcessingState( processingState );
		}
		initializeInstance( );
	}

	private void initializeInstance() {
		stateInjected = false;
		extractRowState( wrappedProcessingState );
		prepareCompositeInstance( wrappedProcessingState );
		if ( isParentInitialized ) {
			return;
		}
		if ( !stateInjected ) {
			handleParentInjection( wrappedProcessingState );
		}

		if ( compositeInstance != NULL_MARKER ) {
			notifyResolutionListeners( compositeInstance );

			final LazyInitializer lazyInitializer = HibernateProxy.extractLazyInitializer( compositeInstance );
			// If the composite instance has a lazy initializer attached, this means that the embeddable is actually virtual
			// and the compositeInstance == entity, so we have to inject the row state into the entity when it finishes resolution
			if ( lazyInitializer != null ) {
				final Initializer parentInitializer = wrappedProcessingState.resolveInitializer( navigablePath.getParent() );
				if ( parentInitializer != this ) {
					( (FetchParentAccess) parentInitializer ).registerResolutionListener( (entity) -> {
						representationEmbeddable.setValues( entity, rowState );
						stateInjected = true;
					} );
				}
				else {
					// At this point, createEmptyCompositesEnabled is always true, so we generate
					// the composite instance.
					//
					// NOTE: `valuesAccess` is set to null to indicate that all values are null,
					//		as opposed to returning the all-null value array.  the instantiator
					//		interprets that as the values are not known or were all null.
					final Object target = representationStrategy
							.getInstantiator()
							.instantiate( this, sessionFactory);
					stateInjected = true;
					lazyInitializer.setImplementation( target );
				}
			}
			else if ( stateAllNull == FALSE && stateInjected != TRUE ) {
				representationEmbeddable.setValues( compositeInstance, rowState );
				stateInjected = true;
			}
		}
	}

	private void prepareCompositeInstance(RowProcessingState processingState) {
		if ( compositeInstance != null ) {
			return;
		}

		// Virtual model parts use the owning entity as container which the fetch parent access provides.
		// For an identifier or foreign key this is called during the resolveKey phase of the fetch parent,
		// so we can't use the fetch parent access in that case.
		if ( fetchParentAccess != null && embedded instanceof VirtualModelPart
				&& !EntityIdentifierMapping.ROLE_LOCAL_NAME.equals( embedded.getFetchableName() )
				&& !ForeignKeyDescriptor.PART_NAME.equals( navigablePath.getLocalName() )
				&& !ForeignKeyDescriptor.TARGET_PART_NAME.equals( navigablePath.getLocalName() ) ) {
			fetchParentAccess.resolveInstance( processingState );
			compositeInstance = fetchParentAccess.getInitializedInstance();
			EntityInitializer entityInitializer = fetchParentAccess.asEntityInitializer();
			if ( entityInitializer != null && entityInitializer.isEntityInitialized() ) {
				this.isParentInitialized = true;
				return;
			}
		}

		if ( compositeInstance == null ) {
			compositeInstance = createCompositeInstance(
					navigablePath,
					representationStrategy,
					sessionFactory
			);
		}

		EmbeddableLoadingLogger.EMBEDDED_LOAD_LOGGER.debugf(
				"Created composite instance [%s]",
				navigablePath
		);
	}

	private boolean isParentInstanceNull() {
		if ( embedded instanceof CompositeIdentifierMapping ) {
			return false;
		}
		FetchParentAccess parentAccess = fetchParentAccess;

		while ( parentAccess != null && parentAccess.isEmbeddableInitializer() ) {
			if ( parentAccess.getInitializedPart() instanceof CompositeIdentifierMapping ) {
				return false;
			}
			parentAccess = parentAccess.getFetchParentAccess();
		}

		if ( parentAccess == null ) {
			return false;
		}

		final EntityInitializer entityInitializer = parentAccess.asEntityInitializer();
		if ( entityInitializer != null && entityInitializer.getParentKey() == null ) {
			return true;
		}
		return false;
	}

	private void extractRowState(RowProcessingState processingState) {
		stateAllNull = true;
		final boolean isKey = ForeignKeyDescriptor.PART_NAME.equals( navigablePath.getLocalName() )
				|| ForeignKeyDescriptor.TARGET_PART_NAME.equals( navigablePath.getLocalName() )
				|| EntityIdentifierMapping.ROLE_LOCAL_NAME.equals( embedded.getFetchableName() );
		for ( int i = 0; i < assemblers.size(); i++ ) {
			final DomainResultAssembler<?> assembler = assemblers.get( i );
			final Object contributorValue = assembler.assemble(
					processingState,
					processingState.getJdbcValuesSourceProcessingState().getProcessingOptions()
			);

			if ( contributorValue == BATCH_PROPERTY ) {
				rowState[i] = null;
			}
			else {
				rowState[i] = contributorValue;
			}
			if ( contributorValue != null ) {
				stateAllNull = false;
			}
			else if ( isKey ) {
				// If this is a foreign key and there is a null part, the whole thing has to be turned into null
				stateAllNull = true;
				break;
			}
		}

		applyMapsId( processingState );
	}

	private void applyMapsId(RowProcessingState processingState) {
		final SharedSessionContractImplementor session = processingState.getSession();
		if ( embedded instanceof CompositeIdentifierMapping ) {
			final CompositeIdentifierMapping cid = (CompositeIdentifierMapping) embedded;
			final EmbeddableMappingType mappedIdEmbeddable = cid.getMappedIdEmbeddableTypeDescriptor();
			if ( cid.hasContainingClass() ) {
				final EmbeddableMappingType virtualIdEmbeddable = embedded.getEmbeddableTypeDescriptor();
				if ( virtualIdEmbeddable == mappedIdEmbeddable ) {
					return;
				}

				virtualIdEmbeddable.forEachAttributeMapping(
						(position, virtualIdAttribute) -> {
							final AttributeMapping mappedIdAttribute = mappedIdEmbeddable.getAttributeMapping( position );

							if ( virtualIdAttribute instanceof ToOneAttributeMapping
									&& !( mappedIdAttribute instanceof ToOneAttributeMapping ) ) {
								final ToOneAttributeMapping toOneAttributeMapping = (ToOneAttributeMapping) virtualIdAttribute;
								final ForeignKeyDescriptor fkDescriptor = toOneAttributeMapping.getForeignKeyDescriptor();
								final Object associationKey = fkDescriptor.getAssociationKeyFromSide(
										rowState[position],
										toOneAttributeMapping.getSideNature().inverse(),
										session
								);
								rowState[position] = associationKey;
							}
						}
				);
			}
		}
	}

	private Object createCompositeInstance(
			NavigablePath navigablePath,
			EmbeddableRepresentationStrategy representationStrategy,
			SessionFactoryImplementor sessionFactory) {
		if ( !createEmptyCompositesEnabled && stateAllNull == TRUE ) {
			return NULL_MARKER;
		}

		final Object instance = representationStrategy.getInstantiator().instantiate( this, sessionFactory );
		stateInjected = true;

		EmbeddableLoadingLogger.EMBEDDED_LOAD_LOGGER.debugf( "Created composite instance [%s] : %s", navigablePath, instance );

		return instance;
	}

	@Override
	public Object[] getValues() {
		return stateAllNull ? null : rowState;
	}

	@Override
	public <T> T getValue(int i, Class<T> clazz) {
		return stateAllNull ? null : clazz.cast( rowState[i] );
	}

	@Override
	public Object getOwner() {
		return fetchParentAccess.getInitializedInstance();
	}

	private void handleParentInjection(RowProcessingState processingState) {
		// todo (6.0) : should we initialize the composite instance if we get here and it is null (not NULL_MARKER)?

		// we want to avoid injection for `NULL_MARKER`
		if ( compositeInstance == null || compositeInstance == NULL_MARKER ) {
			EmbeddableLoadingLogger.EMBEDDED_LOAD_LOGGER.debugf(
					"Skipping parent injection for null embeddable [%s]",
					navigablePath
			);
			return;
		}

		final PropertyAccess parentInjectionAccess = embedded.getParentInjectionAttributePropertyAccess();
		if ( parentInjectionAccess == null ) {
			// embeddable defined no parent injection
			return;
		}

		final Object parent = determineParentInstance( processingState );
		if ( parent == null ) {
			EmbeddableLoadingLogger.EMBEDDED_LOAD_LOGGER.debugf(
					"Unable to determine parent for injection into embeddable [%s]",
					navigablePath
			);
			return;
		}

		EmbeddableLoadingLogger.EMBEDDED_LOAD_LOGGER.debugf(
				"Injecting parent into embeddable [%s] : `%s` -> `%s`",
				navigablePath,
				parent,
				compositeInstance
		);

		parentInjectionAccess.getSetter().set( compositeInstance, parent );
	}

	private Object determineParentInstance(RowProcessingState processingState) {
		// use `fetchParentAccess` if it is available - it is more efficient
		// and the correct way to do it.

		// NOTE: indicates that we are initializing a DomainResult as opposed to a Fetch
		// todo (6.0) - this^^ does not work atm when the embeddable is the key or
		//  element of a collection because it passes in null as the fetch-parent-access.
		//  it should really pass the collection-initializer as the fetch-parent,
		//  or at least the fetch-parent of the collection could get passed.
		if ( fetchParentAccess != null ) {
			// the embeddable being initialized is a fetch, so use the fetchParentAccess
			// to get the parent reference
			//
			// at the moment, this uses the legacy behavior of injecting the "first
			// containing entity" as the parent.  however,
			// todo (6.x) - allow injection of containing composite as parent if
			//  	it is the direct parent

			final FetchParentAccess firstEntityDescriptorAccess = fetchParentAccess.findFirstEntityDescriptorAccess();
			return firstEntityDescriptorAccess.getInitializedInstance();
		}

		// Otherwise, fallback to determining the parent-initializer by path
		//		todo (6.0) - this is the part that should be "subsumed" based on the
		//			comment above

		final NavigablePath parentPath = navigablePath.getParent();
		if ( parentPath == null ) {
			return null;
		}

		final Initializer parentInitializer = processingState.resolveInitializer( parentPath );

		if ( parentInitializer.isCollectionInitializer() ) {
			return ( (CollectionInitializer) parentInitializer ).getCollectionInstance().getOwner();
		}

		final EntityInitializer parentEntityInitializer = parentInitializer.asEntityInitializer();
		if ( parentEntityInitializer != null ) {
			return parentEntityInitializer.getEntityInstance();
		}

		throw new UnsupportedOperationException();
	}

	@Override
	public void finishUpRow(RowProcessingState rowProcessingState) {
		compositeInstance = null;
		stateAllNull = null;
		stateInjected = null;
		isParentInitialized = false;
		wrappedProcessingState = null;

		clearResolutionListeners();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + navigablePath + ") : `" + getInitializedPart().getJavaType().getJavaTypeClass() + "`";
	}
}
