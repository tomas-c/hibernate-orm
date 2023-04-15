/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.hibernate.HibernateException;
import org.hibernate.cache.MutableCacheKeyBuilder;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.IndexedConsumer;
import org.hibernate.loader.ast.internal.CompoundNaturalIdLoader;
import org.hibernate.loader.ast.internal.MultiNaturalIdLoaderStandard;
import org.hibernate.loader.ast.spi.MultiNaturalIdLoader;
import org.hibernate.loader.ast.spi.NaturalIdLoader;
import org.hibernate.metamodel.UnsupportedMappingException;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.AttributeMetadata;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingType;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.NaturalIdMapping;
import org.hibernate.metamodel.mapping.SelectableConsumer;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.results.graph.AssemblerCreationState;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultAssembler;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.FetchParentAccess;
import org.hibernate.sql.results.graph.Fetchable;
import org.hibernate.sql.results.graph.FetchableContainer;
import org.hibernate.sql.results.graph.entity.EntityInitializer;
import org.hibernate.sql.results.graph.internal.ImmutableFetchList;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesSourceProcessingOptions;
import org.hibernate.sql.results.jdbc.spi.RowProcessingState;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * Multi-attribute NaturalIdMapping implementation
 */
public class CompoundNaturalIdMapping extends AbstractNaturalIdMapping implements MappingType, FetchableContainer {

	// todo (6.0) : create a composite MappingType for this descriptor's Object[]?

	private final List<SingularAttributeMapping> attributes;

	private List<JdbcMapping> jdbcMappings;

	public CompoundNaturalIdMapping(
			EntityMappingType declaringType,
			List<SingularAttributeMapping> attributes,
			MappingModelCreationProcess creationProcess) {
		super( declaringType, isMutable( attributes ) );
		this.attributes = attributes;

		creationProcess.registerInitializationCallback(
				"Determine compound natural-id JDBC mappings ( " + declaringType.getEntityName() + ")",
				() -> {
					final List<JdbcMapping> jdbcMappings = new ArrayList<>();
					attributes.forEach(
							(attribute) -> attribute.forEachJdbcType(
									(index, jdbcMapping) -> jdbcMappings.add( jdbcMapping )
							)
					);
					this.jdbcMappings = jdbcMappings;

					return true;
				}
		);
	}

	private static boolean isMutable(List<SingularAttributeMapping> attributes) {
		for ( int i = 0; i < attributes.size(); i++ ) {
			final SingularAttributeMapping attributeMapping = attributes.get( i );
			final AttributeMetadata metadata = attributeMapping.getAttributeMetadata();
			if ( ! metadata.isUpdatable() ) {
				return false;
			}
		}

		return true;
	}

	@Override
	public Object[] extractNaturalIdFromEntityState(Object[] state) {
		if ( state == null ) {
			return null;
		}

		if ( state.length == attributes.size() ) {
			return state;
		}

		final Object[] values = new Object[ attributes.size() ];

		for ( int i = 0; i <= attributes.size() - 1; i++ ) {
			final SingularAttributeMapping attributeMapping = attributes.get( i );
			values[ i ] = state[ attributeMapping.getStateArrayPosition() ];
		}

		return values;
	}

	@Override
	public Object[] extractNaturalIdFromEntity(Object entity) {
		final Object[] values = new Object[ attributes.size() ];

		for ( int i = 0; i < attributes.size(); i++ ) {
			values[i] = attributes.get( i ).getPropertyAccess().getGetter().get( entity );
		}

		return values;
	}

	@Override
	@SuppressWarnings( "rawtypes" )
	public Object[] normalizeInput(Object incoming) {
		if ( incoming instanceof Object[] ) {
			return (Object[]) incoming;
		}

		if ( incoming instanceof Map ) {
			final Map valueMap = (Map) incoming;
			final List<SingularAttributeMapping> attributes = getNaturalIdAttributes();
			final Object[] values = new Object[ attributes.size() ];
			for ( int i = 0; i < attributes.size(); i++ ) {
				values[ i ] = valueMap.get( attributes.get( i ).getAttributeName() );
			}
			return values;
		}

		throw new UnsupportedMappingException( "Do not know how to normalize compound natural-id value : " + incoming );
	}

	@Override
	public void validateInternalForm(Object naturalIdValue) {
		if ( naturalIdValue == null ) {
			return;
		}

		// should be an array, with a size equal to the number of attributes making up this compound natural-id
		if ( naturalIdValue instanceof Object[] ) {
			final Object[] values = (Object[]) naturalIdValue;
			if ( values.length != attributes.size() ) {
				throw new IllegalArgumentException(
						"Natural-id value [" + naturalIdValue + "] did not contain the expected number of elements ["
								+ attributes.size() + "]"
				);
			}

			return;
		}

		throw new IllegalArgumentException( "Natural-id value [" + naturalIdValue + "] was not an array as expected" );
	}

	@Override
	public int calculateHashCode(Object value) {
		if ( value == null ) {
			return 0;
		}
		Object[] values = (Object[]) value;
		int hashcode = 0;
		for ( int i = 0; i < attributes.size(); i++ ) {
			final Object o = values[i];
			if ( o != null ) {
				hashcode = 27 * hashcode + ( (JavaType) attributes.get( i ).getExpressibleJavaType() ).extractHashCode( o );
			}
		}
		return hashcode;
	}

	@Override
	public void verifyFlushState(Object id, Object[] currentState, Object[] loadedState, SharedSessionContractImplementor session) {
		if ( isMutable() ) {
			// EARLY EXIT!!!
			// the natural id is mutable (!immutable), no need to do the checks
			return;
		}

		final PersistenceContext persistenceContext = session.getPersistenceContextInternal();
		final EntityPersister persister = getDeclaringType().getEntityPersister();

		final Object[] naturalId = extractNaturalIdFromEntityState( currentState );

		final Object snapshot = loadedState == null
				? persistenceContext.getNaturalIdSnapshot( id, persister )
				: persister.getNaturalIdMapping().extractNaturalIdFromEntityState( loadedState );
		final Object[] previousNaturalId = (Object[]) snapshot;

		assert naturalId.length == getNaturalIdAttributes().size();
		assert previousNaturalId.length == naturalId.length;

		for ( int i = 0; i < getNaturalIdAttributes().size(); i++ ) {
			final SingularAttributeMapping attributeMapping = getNaturalIdAttributes().get( i );

			final boolean updatable = attributeMapping.getAttributeMetadata().isUpdatable();
			if ( updatable ) {
				// property is updatable (mutable), there is nothing to check
				continue;
			}

			final Object currentValue = naturalId[ i ];
			final Object previousValue = previousNaturalId[ i ];

			if ( ! attributeMapping.areEqual( currentValue, previousValue, session ) ) {
				throw new HibernateException(
						String.format(
								"An immutable attribute [%s] within compound natural identifier of entity %s was altered from `%s` to `%s`",
								attributeMapping.getAttributeName(),
								persister.getEntityName(),
								previousValue,
								currentValue
						)
				);
			}
		}
	}

	@Override
	public boolean areEqual(Object one, Object other, SharedSessionContractImplementor session) {
		return Arrays.equals( (Object[]) one, (Object[]) other );
	}

	@Override
	public List<SingularAttributeMapping> getNaturalIdAttributes() {
		return attributes;
	}

	@Override
	public NaturalIdLoader<?> makeLoader(EntityMappingType entityDescriptor) {
		return new CompoundNaturalIdLoader<>( this, entityDescriptor );
	}

	@Override
	public MultiNaturalIdLoader<?> makeMultiLoader(EntityMappingType entityDescriptor) {
		return new MultiNaturalIdLoaderStandard<>( entityDescriptor );
	}

	@Override
	public MappingType getPartMappingType() {
		return this;
	}

	@Override
	public JavaType<?> getJavaType() {
		// the JavaType is the entity itself
		return getDeclaringType().getJavaType();
	}

	@Override
	public JavaType<?> getMappedJavaType() {
		return getJavaType();
	}

	@Override
	public boolean hasPartitionedSelectionMapping() {
		for ( AttributeMapping attributeMapping : attributes ) {
			if ( attributeMapping.hasPartitionedSelectionMapping() ) {
				return true;
			}
		}
		return false;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ModelPart

	@Override
	public <T> DomainResult<T> createDomainResult(NavigablePath navigablePath, TableGroup tableGroup, String resultVariable, DomainResultCreationState creationState) {
		assert navigablePath.getLocalName().equals( NaturalIdMapping.PART_NAME );

		final SessionFactoryImplementor sessionFactory = creationState.getSqlAstCreationState().getCreationContext().getSessionFactory();

		final JavaType<Object[]> jtd = sessionFactory
				.getTypeConfiguration()
				.getJavaTypeRegistry()
				.getDescriptor( Object[].class );

		// register the table group under `...{natural-id}` as well
		creationState.getSqlAstCreationState().getFromClauseAccess().resolveTableGroup(
				navigablePath,
				(np) -> tableGroup
		);

		return (DomainResult<T>) new DomainResultImpl(
				navigablePath,
				this,
				jtd,
				resultVariable,
				creationState
		);
	}

	@Override
	public void applySqlSelections(NavigablePath navigablePath, TableGroup tableGroup, DomainResultCreationState creationState) {
		for ( int i = 0; i < attributes.size(); i++ ) {
			attributes.get( i ).applySqlSelections( navigablePath, tableGroup, creationState );
		}
	}

	@Override
	public void applySqlSelections(NavigablePath navigablePath, TableGroup tableGroup, DomainResultCreationState creationState, BiConsumer<SqlSelection, JdbcMapping> selectionConsumer) {
		for ( int i = 0; i < attributes.size(); i++ ) {
			attributes.get( i ).applySqlSelections( navigablePath, tableGroup, creationState, selectionConsumer );
		}
	}

	@Override
	public <X, Y> int breakDownJdbcValues(
			Object domainValue,
			int offset,
			X x,
			Y y,
			JdbcValueBiConsumer<X, Y> valueConsumer,
			SharedSessionContractImplementor session) {
		int span = 0;
		if ( domainValue == null ) {
			for ( int i = 0; i < attributes.size(); i++ ) {
				span += attributes.get( i ).breakDownJdbcValues( null, offset + span, x, y, valueConsumer, session );
			}
		}
		else {
			assert domainValue instanceof Object[];

			final Object[] values = (Object[]) domainValue;
			assert values.length == attributes.size();

			for ( int i = 0; i < attributes.size(); i++ ) {
				span += attributes.get( i ).breakDownJdbcValues(
						values[i],
						offset + span,
						x,
						y,
						valueConsumer,
						session
				);
			}
		}
		return span;
	}

	@Override
	public int forEachSelectable(int offset, SelectableConsumer consumer) {
		int span = 0;
		for ( int i = 0; i < attributes.size(); i++ ) {
			span += attributes.get( i ).forEachSelectable( span + offset, consumer );
		}
		return span;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Bindable

	@Override
	public int getJdbcTypeCount() {
		return jdbcMappings.size();
	}

	@Override
	public List<JdbcMapping> getJdbcMappings() {
		return jdbcMappings;
	}

	@Override
	public JdbcMapping getJdbcMapping(int index) {
		return jdbcMappings.get( index );
	}

	@Override
	public int forEachJdbcType(int offset, IndexedConsumer<JdbcMapping> action) {
		int span = 0;
		for ( ; span < jdbcMappings.size(); span++ ) {
			action.accept( span + offset, jdbcMappings.get( span ) );
		}
		return span;
	}

	@Override
	public Object disassemble(Object value, SharedSessionContractImplementor session) {
		if ( value == null ) {
			return null;
		}
		assert value instanceof Object[];

		final Object[] incoming = (Object[]) value;
		assert incoming.length == attributes.size();

		final Object[] outgoing = new Object[ incoming.length ];

		for ( int i = 0; i < attributes.size(); i++ ) {
			final SingularAttributeMapping attribute = attributes.get( i );
			outgoing[ i ] = attribute.disassemble( incoming[ i ], session );
		}

		return outgoing;
	}

	@Override
	public void addToCacheKey(MutableCacheKeyBuilder cacheKey, Object value, SharedSessionContractImplementor session) {
		if ( value == null ) {
			for ( int i = 0; i < attributes.size(); i++ ) {
				attributes.get( i ).addToCacheKey( cacheKey, null, session );
			}
		}
		else {
			assert value instanceof Object[];

			final Object[] values = (Object[]) value;
			assert values.length == attributes.size();

			for ( int i = 0; i < attributes.size(); i++ ) {
				attributes.get( i ).addToCacheKey( cacheKey, values[i], session );
			}
		}
	}

	@Override
	public <X, Y> int forEachDisassembledJdbcValue(
			Object value,
			int offset,
			X x,
			Y y,
			JdbcValuesBiConsumer<X, Y> valuesConsumer,
			SharedSessionContractImplementor session) {
		int span = 0;
		if ( value == null ) {
			for ( int i = 0; i < attributes.size(); i++ ) {
				final SingularAttributeMapping attribute = attributes.get( i );
				span += attribute.forEachDisassembledJdbcValue(
						null,
						span + offset,
						x,
						y,
						valuesConsumer,
						session
				);
			}
		}
		else {
			assert value instanceof Object[];

			final Object[] incoming = (Object[]) value;
			assert incoming.length == attributes.size();
			for ( int i = 0; i < attributes.size(); i++ ) {
				final SingularAttributeMapping attribute = attributes.get( i );
				span += attribute.forEachDisassembledJdbcValue(
						incoming[i],
						span + offset,
						x,
						y,
						valuesConsumer,
						session
				);
			}
		}
		return span;
	}

	@Override
	public <X, Y> int forEachJdbcValue(
			Object value,
			int offset,
			X x,
			Y y,
			JdbcValuesBiConsumer<X, Y> valuesConsumer,
			SharedSessionContractImplementor session) {
		int span = 0;
		if ( value == null ) {
			for ( int i = 0; i < attributes.size(); i++ ) {
				final SingularAttributeMapping attribute = attributes.get( i );
				span += attribute.forEachJdbcValue( null, span + offset, x, y, valuesConsumer, session );
			}
		}
		else {
			assert value instanceof Object[];

			final Object[] incoming = (Object[]) value;
			assert incoming.length == attributes.size();

			for ( int i = 0; i < attributes.size(); i++ ) {
				final SingularAttributeMapping attribute = attributes.get( i );
				span += attribute.forEachJdbcValue( incoming[i], span + offset, x, y, valuesConsumer, session );
			}
		}
		return span;
	}

	@Override
	public int getNumberOfFetchables() {
		return attributes.size();
	}

	@Override
	public Fetchable getFetchable(int position) {
		return attributes.get( position );
	}

	@Override
	public ModelPart findSubPart(String name, EntityMappingType treatTargetType) {
		for ( int i = 0; i < attributes.size(); i++ ) {
			if ( name.equals( attributes.get( i ).getAttributeName() ) ) {
				return attributes.get( i );
			}
		}
		return null;
	}

	@Override
	public void forEachSubPart(IndexedConsumer<ModelPart> consumer, EntityMappingType treatTarget) {
		for ( int i = 0; i < attributes.size(); i++ ) {
			consumer.accept( i, attributes.get(i) );
		}
	}

	@Override
	public void visitSubParts(Consumer<ModelPart> consumer, EntityMappingType treatTargetType) {
		attributes.forEach( consumer );
	}


	public static class DomainResultImpl implements DomainResult<Object[]>, FetchParent {
		private final NavigablePath navigablePath;
		private final CompoundNaturalIdMapping naturalIdMapping;
		private final JavaType<Object[]> arrayJtd;

		private final ImmutableFetchList fetches;
		private final boolean hasJoinFetches;
		private final boolean containsCollectionFetches;

		private final String resultVariable;

		public DomainResultImpl(
				NavigablePath navigablePath,
				CompoundNaturalIdMapping naturalIdMapping,
				JavaType<Object[]> arrayJtd,
				String resultVariable,
				DomainResultCreationState creationState) {
			this.navigablePath = navigablePath;
			this.naturalIdMapping = naturalIdMapping;
			this.arrayJtd = arrayJtd;
			this.resultVariable = resultVariable;

			this.fetches = creationState.visitFetches( this );
			this.hasJoinFetches = this.fetches.hasJoinFetches();
			this.containsCollectionFetches = this.fetches.containsCollectionFetches();
		}

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// DomainResult

		@Override
		public String getResultVariable() {
			return resultVariable;
		}

		@Override
		public DomainResultAssembler<Object[]> createResultAssembler(
				FetchParentAccess parentAccess,
				AssemblerCreationState creationState) {
			return new AssemblerImpl(
					fetches,
					navigablePath,
					naturalIdMapping,
					arrayJtd,
					creationState
			);
		}

		@Override
		public JavaType<Object[]> getResultJavaType() {
			return arrayJtd;
		}


		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// FetchParent

		@Override
		public FetchableContainer getReferencedMappingContainer() {
			return getReferencedMappingType();
		}

		@Override
		public FetchableContainer getReferencedMappingType() {
			return naturalIdMapping;
		}

		@Override
		public NavigablePath getNavigablePath() {
			return navigablePath;
		}

		@Override
		public ImmutableFetchList getFetches() {
			return fetches;
		}

		@Override
		public Fetch findFetch(Fetchable fetchable) {
			assert fetchable != null;
			return fetches.get( fetchable );
		}

		@Override
		public boolean hasJoinFetches() {
			return hasJoinFetches;
		}

		@Override
		public boolean containsCollectionFetches() {
			return containsCollectionFetches;
		}
	}

	private static class AssemblerImpl implements DomainResultAssembler<Object[]> {
		private final JavaType<Object[]> jtd;

		private final DomainResultAssembler<?>[] subAssemblers;

		private AssemblerImpl(
				ImmutableFetchList fetches,
				NavigablePath navigablePath,
				CompoundNaturalIdMapping naturalIdMapping,
				JavaType<Object[]> jtd,
				AssemblerCreationState creationState) {
			this.jtd = jtd;

			// we don't even register the Initializer here... its really no-op.
			// we just "need it" as an impl detail for handling Fetches
			final InitializerImpl initializer = new InitializerImpl( navigablePath, naturalIdMapping );

			this.subAssemblers = new DomainResultAssembler[fetches.size()];
			int i = 0;
			for ( Fetch fetch : fetches ) {
				subAssemblers[i++] = fetch.createAssembler( initializer, creationState );
			}
		}

		@Override
		public Object[] assemble(
				RowProcessingState rowProcessingState,
				JdbcValuesSourceProcessingOptions options) {
			final Object[] result = new Object[ subAssemblers.length ];
			for ( int i = 0; i < subAssemblers.length; i++ ) {
				result[ i ] = subAssemblers[i].assemble( rowProcessingState, options );
			}
			return result;
		}

		@Override
		public JavaType<Object[]> getAssembledJavaType() {
			return jtd;
		}
	}

	private static class InitializerImpl implements FetchParentAccess {
		private final NavigablePath navigablePath;
		private final CompoundNaturalIdMapping naturalIdMapping;

		public InitializerImpl(NavigablePath navigablePath, CompoundNaturalIdMapping naturalIdMapping) {
			this.navigablePath = navigablePath;
			this.naturalIdMapping = naturalIdMapping;
		}

		@Override
		public FetchParentAccess findFirstEntityDescriptorAccess() {
			return null;
		}

		@Override
		public EntityInitializer findFirstEntityInitializer() {
			return null;
		}

		@Override
		public Object getParentKey() {
			return null;
		}

		@Override
		public NavigablePath getNavigablePath() {
			return navigablePath;
		}

		@Override
		public ModelPart getInitializedPart() {
			return naturalIdMapping;
		}

		@Override
		public Object getInitializedInstance() {
			return null;
		}

		@Override
		public void resolveKey(RowProcessingState rowProcessingState) {

		}

		@Override
		public void resolveInstance(RowProcessingState rowProcessingState) {

		}

		@Override
		public void initializeInstance(RowProcessingState rowProcessingState) {

		}

		@Override
		public void finishUpRow(RowProcessingState rowProcessingState) {

		}

		@Override
		public void registerResolutionListener(Consumer<Object> resolvedParentConsumer) {

		}
	}
}
