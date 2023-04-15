/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Internal;
import org.hibernate.MappingException;
import org.hibernate.Remove;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.ExportableProducer;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.model.source.internal.hbm.MappingDocument;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.registry.classloading.spi.ClassLoadingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.CompositeNestedGeneratedValueGenerator;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.factory.IdentifierGeneratorFactory;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.internal.util.collections.JoinedIterator;
import org.hibernate.metamodel.spi.EmbeddableInstantiator;
import org.hibernate.property.access.spi.Setter;
import org.hibernate.generator.Generator;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.type.ComponentType;
import org.hibernate.type.CompositeType;
import org.hibernate.type.EmbeddedComponentType;
import org.hibernate.type.Type;

import static org.hibernate.generator.EventType.INSERT;
import static org.hibernate.id.IdentifierGeneratorHelper.POST_INSERT_INDICATOR;

/**
 * A mapping model object that represents an {@linkplain jakarta.persistence.Embeddable embeddable class}.
 *
 * @apiNote The name of this class is historical and unfortunate. An embeddable class holds a "component"
 *          of the state of an entity. It has absolutely nothing to do with modularity in software engineering.
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public class Component extends SimpleValue implements MetaAttributable, SortableValue {

	private String componentClassName;
	private boolean embedded;
	private String parentProperty;
	private PersistentClass owner;
	private boolean dynamic;
	private boolean isKey;
	private String roleName;

	private final ArrayList<Property> properties = new ArrayList<>();
	private int[] originalPropertyOrder = ArrayHelper.EMPTY_INT_ARRAY;
	private Map<String,MetaAttribute> metaAttributes;

	private Class<? extends EmbeddableInstantiator> customInstantiator;
	private Constructor<?> instantiator;
	private String[] instantiatorPropertyNames;

	// cache the status of the type
	private volatile CompositeType type;

	private AggregateColumn aggregateColumn;
	private AggregateColumn parentAggregateColumn;
	private String structName;
	private String[] structColumnNames;
	// lazily computed based on 'properties' field: invalidate by setting to null when properties are modified
	private transient List<Selectable> cachedSelectables;
	// lazily computed based on 'properties' field: invalidate by setting to null when properties are modified
	private transient List<Column> cachedColumns;

	private transient Generator builtIdentifierGenerator;

	public Component(MetadataBuildingContext metadata, PersistentClass owner) throws MappingException {
		this( metadata, owner.getTable(), owner );
	}

	public Component(MetadataBuildingContext metadata, Component component) throws MappingException {
		this( metadata, component.getTable(), component.getOwner() );
	}

	public Component(MetadataBuildingContext metadata, Join join) throws MappingException {
		this( metadata, join.getTable(), join.getPersistentClass() );
	}

	public Component(MetadataBuildingContext metadata, Collection collection) throws MappingException {
		this( metadata, collection.getCollectionTable(), collection.getOwner() );
	}

	public Component(MetadataBuildingContext metadata, Table table, PersistentClass owner) throws MappingException {
		super( metadata, table );
		this.owner = owner;

		metadata.getMetadataCollector().registerComponent( this );
	}

	private Component(Component original) {
		super( original );
		this.properties.addAll( original.properties );
		this.originalPropertyOrder = original.originalPropertyOrder.clone();
		this.componentClassName = original.componentClassName;
		this.embedded = original.embedded;
		this.parentProperty = original.parentProperty;
		this.owner = original.owner;
		this.dynamic = original.dynamic;
		this.metaAttributes = original.metaAttributes == null ? null : new HashMap<>(original.metaAttributes);
		this.isKey = original.isKey;
		this.roleName = original.roleName;
		this.customInstantiator = original.customInstantiator;
		this.type = original.type;
	}

	@Override
	public Component copy() {
		return new Component( this );
	}

	public int getPropertySpan() {
		return properties.size();
	}

	@Deprecated @Remove
	public Iterator<Property> getPropertyIterator() {
		return properties.iterator();
	}

	public List<Property> getProperties() {
		return properties;
	}

	public void addProperty(Property p) {
		properties.add( p );
		propertiesListModified();
	}

	private void propertiesListModified() {
		this.cachedSelectables = null;
		this.cachedColumns = null;
	}

	@Override
	public void addColumn(Column column) {
		throw new UnsupportedOperationException("Cant add a column to a component");
	}

	@Override
	public int getColumnSpan() {
		return getSelectables().size();
	}

	@Override @Deprecated @SuppressWarnings("deprecation")
	public Iterator<Selectable> getColumnIterator() {
		@SuppressWarnings("unchecked")
		Iterator<Selectable>[] iters = new Iterator[ getPropertySpan() ];
		int i = 0;
		for ( Property property : getProperties() ) {
			iters[i++] = property.getColumnIterator();
		}
		return new JoinedIterator<>( iters );
	}

	@Override
	public List<Selectable> getSelectables() {
		if ( cachedSelectables == null ) {
			cachedSelectables = properties.stream()
					.flatMap(p -> p.getSelectables().stream())
					.collect(Collectors.toList());
		}
		return cachedSelectables;
	}

	@Override
	public List<Column> getColumns() {
		if ( cachedColumns != null ) {
			return cachedColumns;
		}
		else {
			this.cachedColumns = properties.stream()
					.flatMap( p -> p.getValue().getColumns().stream() )
					.collect( Collectors.toList() );
			return cachedColumns;
		}
	}

	public boolean isEmbedded() {
		return embedded;
	}

	public AggregateColumn getAggregateColumn() {
		return aggregateColumn;
	}

	public void setAggregateColumn(AggregateColumn aggregateColumn) {
		this.aggregateColumn = aggregateColumn;
		notifyPropertiesAboutAggregateColumn( aggregateColumn, this );
	}

	public List<Column> getAggregatedColumns() {
		final List<Column> aggregatedColumns = new ArrayList<>( getPropertySpan() );
		collectAggregatedColumns( aggregatedColumns, this );
		return aggregatedColumns;
	}

	private void collectAggregatedColumns(List<Column> aggregatedColumns, Component component) {
		for ( Property property : component.getProperties() ) {
			final Value value = property.getValue();
			if ( value instanceof Component ) {
				final Component subComponent = (Component) value;
				final AggregateColumn subAggregate = subComponent.getAggregateColumn();
				if ( subAggregate != null ) {
					aggregatedColumns.add( subAggregate );
				}
				else {
					collectAggregatedColumns( aggregatedColumns, subComponent );
				}
			}
			else {
				aggregatedColumns.addAll( value.getColumns() );
			}
		}
	}

	private void notifyPropertiesAboutAggregateColumn(AggregateColumn aggregateColumn, Component component) {
		for ( Property property : component.getProperties() ) {
			// Let the BasicValue of every sub-column know about the aggregate,
			// which is needed in type resolution
			final Value value = property.getValue();
			if ( value instanceof BasicValue ) {
				assert ( (BasicValue) value ).getResolution() == null;
				( (BasicValue) value ).setAggregateColumn( aggregateColumn );
			}
			else if ( value instanceof Component ) {
				final Component subComponent = (Component) value;
				if ( subComponent.getAggregateColumn() == null ) {
					subComponent.notifyPropertiesAboutAggregateColumn( aggregateColumn, subComponent );
				}
				else {
					( (Component) value ).setParentAggregateColumn( aggregateColumn );
				}
			}
		}
	}

	public AggregateColumn getParentAggregateColumn() {
		return parentAggregateColumn;
	}

	public void setParentAggregateColumn(AggregateColumn parentAggregateColumn) {
		this.parentAggregateColumn = parentAggregateColumn;
	}

	public String getStructName() {
		return structName;
	}

	public void setStructName(String structName) {
		this.structName = structName;
	}

	protected void checkColumnDuplication() throws MappingException {
		checkPropertyColumnDuplication( new HashSet<>(), getProperties() );
	}
	protected void checkColumnDuplication(java.util.Set<String> distinctColumns, Value value)
			throws MappingException {
		if ( value != null ) {
			for ( Selectable columnOrFormula : value.getSelectables() ) {
				if ( !columnOrFormula.isFormula() ) {
					final Column col = (Column) columnOrFormula;
					if ( !distinctColumns.add( col.getName() ) ) {
						throw new MappingException(
								"Column '" + col.getName()
										+ "' is duplicated in mapping for component '" + getRoleName()
										+ "' (use '@Column(insertable=false, updatable=false)' when mapping multiple properties to the same column)"
						);
					}
				}
			}
		}
	}

	protected void checkPropertyColumnDuplication(Set<String> distinctColumns, List<Property> properties)
			throws MappingException {
		for ( Property prop : properties ) {
			Value value = prop.getValue();
			if ( value instanceof Component ) {
				Component component = (Component) value;
				AggregateColumn aggregateColumn = component.getAggregateColumn();
				if ( aggregateColumn == null ) {
					checkPropertyColumnDuplication( distinctColumns, component.getProperties() );
				}
				else {
					component.checkColumnDuplication();
					checkColumnDuplication( distinctColumns, aggregateColumn.getValue() );
				}
			}
			else {
				if ( prop.isUpdateable() || prop.isInsertable() ) {
					checkColumnDuplication( distinctColumns, value);
				}
			}
		}
	}

	public String getComponentClassName() {
		return componentClassName;
	}

	public Class<?> getComponentClass() throws MappingException {
		final ClassLoaderService classLoaderService = getMetadata()
				.getMetadataBuildingOptions()
				.getServiceRegistry()
				.getService( ClassLoaderService.class );
		try {
			return classLoaderService.classForName( componentClassName );
		}
		catch (ClassLoadingException e) {
			throw new MappingException("component class not found: " + componentClassName, e);
		}
	}

	public PersistentClass getOwner() {
		return owner;
	}

	public String getParentProperty() {
		return parentProperty;
	}

	public void setComponentClassName(String componentClass) {
		this.componentClassName = componentClass;
	}

	public void setEmbedded(boolean embedded) {
		this.embedded = embedded;
	}

	public void setOwner(PersistentClass owner) {
		this.owner = owner;
	}

	public void setParentProperty(String parentProperty) {
		this.parentProperty = parentProperty;
	}

	public boolean isDynamic() {
		return dynamic;
	}

	public void setDynamic(boolean dynamic) {
		this.dynamic = dynamic;
	}

	@Override
	public CompositeType getType() throws MappingException {
		// Resolve the type of the value once and for all as this operation generates a proxy class
		// for each invocation.
		// Unfortunately, there's no better way of doing that as none of the classes are immutable and
		// we can't know for sure the current state of the property or the value.
		CompositeType localType = type;

		if ( localType == null ) {
			synchronized ( this ) {
				localType = type;
				if ( localType == null ) {
					// Make sure this is sorted which is important especially for synthetic components
					// Other components should be sorted already
					sortProperties( true );

					localType = isEmbedded()
							? new EmbeddedComponentType( this, originalPropertyOrder, getBuildingContext() )
							: new ComponentType( this, originalPropertyOrder, getBuildingContext() );

					this.type = localType;
				}
			}
		}

		return localType;
	}

	@Override
	public void setTypeUsingReflection(String className, String propertyName)
		throws MappingException {
	}

	@Override
	public Map<String, MetaAttribute> getMetaAttributes() {
		return metaAttributes;
	}

	@Override
	public MetaAttribute getMetaAttribute(String attributeName) {
		return metaAttributes==null ? null : metaAttributes.get(attributeName);
	}

	@Override
	public void setMetaAttributes(Map<String, MetaAttribute> metas) {
		this.metaAttributes = metas;
	}

	@Override
	public Object accept(ValueVisitor visitor) {
		return visitor.accept(this);
	}

	@Override
	public boolean isSame(SimpleValue other) {
		return other instanceof Component && isSame( (Component) other );
	}

	public boolean isSame(Component other) {
		return super.isSame( other )
				&& Objects.equals( properties, other.properties )
				&& Objects.equals( componentClassName, other.componentClassName )
				&& embedded == other.embedded
				&& Objects.equals( aggregateColumn, other.aggregateColumn )
				&& Objects.equals( parentAggregateColumn, other.parentAggregateColumn )
				&& Objects.equals( structName, other.structName )
				&& Objects.equals( parentProperty, other.parentProperty )
				&& Objects.equals( metaAttributes, other.metaAttributes );
	}

	@Override
	public boolean[] getColumnInsertability() {
		final boolean[] result = new boolean[ getColumnSpan() ];
		int i = 0;
		for ( Property prop : getProperties() ) {
			final boolean[] chunk = prop.getValue().getColumnInsertability();
			if ( prop.isInsertable() ) {
				System.arraycopy( chunk, 0, result, i, chunk.length );
			}
			i += chunk.length;
		}
		return result;
	}

	@Override
	public boolean hasAnyInsertableColumns() {
		for ( Property property : properties ) {
			if ( property.getValue().hasAnyInsertableColumns() ) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean[] getColumnUpdateability() {
		boolean[] result = new boolean[ getColumnSpan() ];
		int i=0;
		for ( Property prop : getProperties() ) {
			boolean[] chunk = prop.getValue().getColumnUpdateability();
			if ( prop.isUpdateable() ) {
				System.arraycopy(chunk, 0, result, i, chunk.length);
			}
			i+=chunk.length;
		}
		return result;
	}

	@Override
	public boolean hasAnyUpdatableColumns() {
		for ( Property property : properties ) {
			if ( property.getValue().hasAnyUpdatableColumns() ) {
				return true;
			}
		}
		return false;
	}

	public boolean isKey() {
		return isKey;
	}

	public void setKey(boolean isKey) {
		this.isKey = isKey;
	}

	public boolean hasPojoRepresentation() {
		return componentClassName!=null;
	}

	/**
	 * Returns the {@link Property} at the specified position in this {@link Component}.
	 *
	 * @param index index of the {@link Property} to return
	 * @return {@link Property}
	 * @throws IndexOutOfBoundsException - if the index is out of range(index &lt; 0 || index &gt;=
	 * {@link #getPropertySpan()})
	 */
	public Property getProperty(int index) {
		return properties.get( index );
	}

	public Property getProperty(String propertyName) throws MappingException {
		for ( Property prop : properties ) {
			if ( prop.getName().equals(propertyName) ) {
				return prop;
			}
		}
		throw new MappingException("component: " + componentClassName + " property not found: " + propertyName);
	}

	public boolean hasProperty(String propertyName) {
		for ( Property prop : properties ) {
			if ( prop.getName().equals(propertyName) ) {
				return true;
			}
		}
		return false;
	}

	public String getRoleName() {
		return roleName;
	}

	public void setRoleName(String roleName) {
		this.roleName = roleName;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + '(' + componentClassName + ')';
	}

	@Override
	public Generator createGenerator(
			IdentifierGeneratorFactory identifierGeneratorFactory,
			Dialect dialect,
			RootClass rootClass) throws MappingException {
		if ( builtIdentifierGenerator == null ) {
			builtIdentifierGenerator = buildIdentifierGenerator(
					identifierGeneratorFactory,
					dialect,
					rootClass
			);
		}
		return builtIdentifierGenerator;
	}

	private Generator buildIdentifierGenerator(
			IdentifierGeneratorFactory identifierGeneratorFactory,
			Dialect dialect,
			RootClass rootClass) throws MappingException {
		final boolean hasCustomGenerator = ! DEFAULT_ID_GEN_STRATEGY.equals( getIdentifierGeneratorStrategy() );
		if ( hasCustomGenerator ) {
			return super.createGenerator( identifierGeneratorFactory, dialect, rootClass );
		}

		final Class<?> entityClass = rootClass.getMappedClass();
		final Class<?> attributeDeclarer; // what class is the declarer of the composite pk attributes
		// IMPL NOTE : See the javadoc discussion on CompositeNestedGeneratedValueGenerator wrt the
		//		various scenarios for which we need to account here
		if ( rootClass.getIdentifierMapper() != null ) {
			// we have the @IdClass / <composite-id mapped="true"/> case
			attributeDeclarer = resolveComponentClass();
		}
		else if ( rootClass.getIdentifierProperty() != null ) {
			// we have the "@EmbeddedId" / <composite-id name="idName"/> case
			attributeDeclarer = resolveComponentClass();
		}
		else {
			// we have the "straight up" embedded (again the Hibernate term) component identifier
			attributeDeclarer = entityClass;
		}

		final CompositeNestedGeneratedValueGenerator.GenerationContextLocator locator =
				new StandardGenerationContextLocator( rootClass.getEntityName() );
		final CompositeNestedGeneratedValueGenerator generator = new CompositeNestedGeneratedValueGenerator( locator );

		for ( Property property : getProperties() ) {
			if ( property.getValue().isSimpleValue() ) {
				final SimpleValue value = (SimpleValue) property.getValue();

				if ( !DEFAULT_ID_GEN_STRATEGY.equals( value.getIdentifierGeneratorStrategy() ) ) {
					// skip any 'assigned' generators, they would have been handled by
					// the StandardGenerationContextLocator
					Generator subgenerator = value.createGenerator( identifierGeneratorFactory, dialect, rootClass );
					generator.addGeneratedValuePlan( new ValueGenerationPlan(
							subgenerator,
							injector( property, attributeDeclarer ) )
					);
				}
			}
		}
		return generator;
	}

	private Setter injector(Property property, Class<?> attributeDeclarer) {
		return property.getPropertyAccessStrategy( attributeDeclarer )
				.buildPropertyAccess( attributeDeclarer, property.getName(), true )
				.getSetter();
	}

	private Class<?> resolveComponentClass() {
		try {
			return getComponentClass();
		}
		catch ( Exception e ) {
			return null;
		}
	}

	@Internal
	public String[] getPropertyNames() {
		final String[] propertyNames = new String[properties.size()];
		for ( int i = 0; i < properties.size(); i++ ) {
			propertyNames[i] = properties.get( i ).getName();
		}
		return propertyNames;
	}

	public static class StandardGenerationContextLocator
			implements CompositeNestedGeneratedValueGenerator.GenerationContextLocator {
		private final String entityName;

		public StandardGenerationContextLocator(String entityName) {
			this.entityName = entityName;
		}

		@Override
		public Object locateGenerationContext(SharedSessionContractImplementor session, Object incomingObject) {
			return session.getEntityPersister( entityName, incomingObject ).getIdentifier( incomingObject, session );
		}
	}

	public static class ValueGenerationPlan implements CompositeNestedGeneratedValueGenerator.GenerationPlan {
		private final Generator subgenerator;
		private final Setter injector;

		public ValueGenerationPlan(Generator subgenerator, Setter injector) {
			this.subgenerator = subgenerator;
			this.injector = injector;
		}

		@Override
		public void execute(SharedSessionContractImplementor session, Object incomingObject, Object injectionContext) {
			if ( !subgenerator.generatedOnExecution() ) {
				Object generatedId = ( (BeforeExecutionGenerator) subgenerator).generate( session, incomingObject, null, INSERT );
				injector.set( injectionContext, generatedId );
			}
			else {
				injector.set( injectionContext, POST_INSERT_INDICATOR );
			}
		}

		@Override
		public void registerExportables(Database database) {
			if ( subgenerator instanceof ExportableProducer ) {
				( (ExportableProducer) subgenerator).registerExportables( database );
			}
		}

		@Override
		public void initialize(SqlStringGenerationContext context) {
			if ( subgenerator instanceof IdentifierGenerator ) {
				( (IdentifierGenerator) subgenerator).initialize( context );
			}
		}
	}

	public void prepareForMappingModel() {
		// This call will initialize the type properly
		getType();
	}

	@Override
	public boolean isValid(Mapping mapping) throws MappingException {
		if ( !super.isValid( mapping ) ) {
			return false;
		}
		if ( instantiatorPropertyNames != null ) {
			if ( instantiatorPropertyNames.length < properties.size() ) {
				throw new MappingException( "component type [" + componentClassName + "] specifies " + instantiatorPropertyNames.length + " properties for the instantiator but has " + properties.size() + " properties" );
			}
			final HashSet<String> assignedPropertyNames = CollectionHelper.setOfSize( properties.size() );
			for ( String instantiatorPropertyName : instantiatorPropertyNames ) {
				if ( getProperty( instantiatorPropertyName ) == null ) {
					throw new MappingException( "could not find property [" + instantiatorPropertyName + "] defined in the @Instantiator withing component [" + componentClassName + "]" );
				}
				assignedPropertyNames.add( instantiatorPropertyName );
			}
			if ( assignedPropertyNames.size() != properties.size() ) {
				final ArrayList<String> missingProperties = new ArrayList<>();
				for ( Property property : properties ) {
					if ( !assignedPropertyNames.contains( property.getName() ) ) {
						missingProperties.add( property.getName() );
					}
				}
				throw new MappingException( "component type [" + componentClassName + "] has " + properties.size() + " properties but the instantiator only assigns " + assignedPropertyNames.size() + " properties. missing properties: " + missingProperties );
			}
		}
		return true;
	}

	@Override
	public boolean isSorted() {
		return originalPropertyOrder != ArrayHelper.EMPTY_INT_ARRAY;
	}

	@Override
	public int[] sortProperties() {
		return sortProperties( false );
	}

	private int[] sortProperties(boolean forceRetainOriginalOrder) {
		if ( originalPropertyOrder != ArrayHelper.EMPTY_INT_ARRAY ) {
			return originalPropertyOrder;
		}
		// Don't sort the properties for a simple record
		if ( isSimpleRecord() ) {
			return this.originalPropertyOrder = null;
		}
		final int[] originalPropertyOrder;
		// We need to capture the original property order if this is an alternate unique key or embedded component property
		// to be able to sort the other side of the foreign key accordingly
		// and also if the source is a XML mapping
		// because XML mappings might refer to this through the defined order
		if ( forceRetainOriginalOrder || isAlternateUniqueKey() || isEmbedded()
				|| getBuildingContext() instanceof MappingDocument ) {
			final Property[] originalProperties = properties.toArray( new Property[0] );
			properties.sort( Comparator.comparing( Property::getName ) );
			originalPropertyOrder = new int[originalProperties.length];
			for ( int j = 0; j < originalPropertyOrder.length; j++ ) {
				originalPropertyOrder[j] = properties.indexOf( originalProperties[j] );
			}
		}
		else {
			properties.sort( Comparator.comparing( Property::getName ) );
			originalPropertyOrder = null;
		}
		if ( isKey ) {
			final PrimaryKey primaryKey = getOwner().getTable().getPrimaryKey();
			if ( primaryKey != null ) {
				// We have to re-order the primary key accordingly
				final List<Column> columns = primaryKey.getColumns();
				columns.clear();
				for ( Property property : properties ) {
					for ( Selectable selectable : property.getSelectables() ) {
						if ( selectable instanceof Column ) {
							columns.add( (Column) selectable );
						}
					}
				}
			}
		}
		propertiesListModified();
		return this.originalPropertyOrder = originalPropertyOrder;
	}

	private boolean isSimpleRecord() {
		// A simple record is given, when the properties match the order of the record component names
		final Class<?> componentClass = resolveComponentClass();
		if ( componentClass == null || !ReflectHelper.isRecord( componentClass ) ) {
			return false;
		}
		final String[] recordComponentNames = ReflectHelper.getRecordComponentNames( componentClass );
		if ( recordComponentNames.length != properties.size() ) {
			return false;
		}
		for ( int i = 0; i < recordComponentNames.length; i++ ) {
			if ( !recordComponentNames[i].equals( properties.get( i ).getName() ) ) {
				return false;
			}
		}

		return true;
	}

	public Class<? extends EmbeddableInstantiator> getCustomInstantiator() {
		return customInstantiator;
	}

	public void setCustomInstantiator(Class<? extends EmbeddableInstantiator> customInstantiator) {
		this.customInstantiator = customInstantiator;
	}

	public Constructor<?> getInstantiator() {
		return instantiator;
	}

	public String[] getInstantiatorPropertyNames() {
		return instantiatorPropertyNames;
	}

	public void setInstantiator(Constructor<?> instantiator, String[] instantiatorPropertyNames) {
		this.instantiator = instantiator;
		this.instantiatorPropertyNames = instantiatorPropertyNames;
	}

	public String[] getStructColumnNames() {
		return structColumnNames;
	}

	public void setStructColumnNames(String[] structColumnNames) {
		this.structColumnNames = structColumnNames;
	}
}
