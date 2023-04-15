/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.spi;

import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.TimeZoneStorageStrategy;
import org.hibernate.boot.model.IdGeneratorStrategyInterpreter;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.relational.ColumnOrderingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.MetadataSourceType;
import org.hibernate.dialect.TimeZoneSupport;
import org.hibernate.id.factory.IdentifierGeneratorFactory;
import org.hibernate.type.WrapperArrayHandling;
import org.hibernate.type.spi.TypeConfiguration;

import jakarta.persistence.SharedCacheMode;

/**
 * Convenience base class for custom implementors of {@link MetadataBuildingOptions} using delegation.
 *
 * @author Gunnar Morling
 * @author Steve Ebersole
 */
@SuppressWarnings("unused")
public abstract class AbstractDelegatingMetadataBuildingOptions implements MetadataBuildingOptions, JpaOrmXmlPersistenceUnitDefaultAware {

	private final MetadataBuildingOptions delegate;

	public AbstractDelegatingMetadataBuildingOptions(MetadataBuildingOptions delegate) {
		this.delegate = delegate;
	}

	protected MetadataBuildingOptions delegate() {
		return delegate;
	}

	@Override
	public StandardServiceRegistry getServiceRegistry() {
		return delegate.getServiceRegistry();
	}

	@Override
	public MappingDefaults getMappingDefaults() {
		return delegate.getMappingDefaults();
	}

	@Override
	public IdentifierGeneratorFactory getIdentifierGeneratorFactory() {
		return delegate.getIdentifierGeneratorFactory();
	}

	@Override
	public TimeZoneStorageStrategy getDefaultTimeZoneStorage() {
		return delegate.getDefaultTimeZoneStorage();
	}

	@Override
	public TimeZoneSupport getTimeZoneSupport() {
		return delegate.getTimeZoneSupport();
	}

	@Override
	public WrapperArrayHandling getWrapperArrayHandling() {
		return delegate.getWrapperArrayHandling();
	}

	@Override
	public List<BasicTypeRegistration> getBasicTypeRegistrations() {
		return delegate.getBasicTypeRegistrations();
	}

	@Override
	public TypeConfiguration getTypeConfiguration() {
		return delegate.getTypeConfiguration();
	}

	@Override
	public ImplicitNamingStrategy getImplicitNamingStrategy() {
		return delegate.getImplicitNamingStrategy();
	}

	@Override
	public PhysicalNamingStrategy getPhysicalNamingStrategy() {
		return delegate.getPhysicalNamingStrategy();
	}

	@Override
	public ColumnOrderingStrategy getColumnOrderingStrategy() {
		return delegate.getColumnOrderingStrategy();
	}

	@Override
	public SharedCacheMode getSharedCacheMode() {
		return delegate.getSharedCacheMode();
	}

	@Override
	public AccessType getImplicitCacheAccessType() {
		return delegate.getImplicitCacheAccessType();
	}

	@Override
	public boolean isMultiTenancyEnabled() {
		return delegate.isMultiTenancyEnabled();
	}

	@Override
	public IdGeneratorStrategyInterpreter getIdGenerationTypeInterpreter() {
		return delegate.getIdGenerationTypeInterpreter();
	}

	@Override
	public boolean ignoreExplicitDiscriminatorsForJoinedInheritance() {
		return delegate.ignoreExplicitDiscriminatorsForJoinedInheritance();
	}

	@Override
	public boolean createImplicitDiscriminatorsForJoinedInheritance() {
		return delegate.createImplicitDiscriminatorsForJoinedInheritance();
	}

	@Override
	public boolean shouldImplicitlyForceDiscriminatorInSelect() {
		return delegate.shouldImplicitlyForceDiscriminatorInSelect();
	}

	@Override
	public boolean useNationalizedCharacterData() {
		return delegate.useNationalizedCharacterData();
	}

	@Override
	public boolean isSpecjProprietarySyntaxEnabled() {
		return delegate.isSpecjProprietarySyntaxEnabled();
	}

	@Override
	public boolean isNoConstraintByDefault() {
		return delegate.isNoConstraintByDefault();
	}

	@Override
	public List<MetadataSourceType> getSourceProcessOrdering() {
		return delegate.getSourceProcessOrdering();
	}

	@Override
	public void apply(JpaOrmXmlPersistenceUnitDefaults jpaOrmXmlPersistenceUnitDefaults) {
		if ( delegate instanceof JpaOrmXmlPersistenceUnitDefaultAware ) {
			( (JpaOrmXmlPersistenceUnitDefaultAware) delegate ).apply( jpaOrmXmlPersistenceUnitDefaults );
		}
		else {
			throw new HibernateException(
					"AbstractDelegatingMetadataBuildingOptions delegate did not " +
							"implement JpaOrmXmlPersistenceUnitDefaultAware; " +
							"cannot delegate JpaOrmXmlPersistenceUnitDefaultAware#apply"
			);
		}
	}

	@Override
	public String getSchemaCharset() {
		return delegate.getSchemaCharset();
	}

	@Override
	public boolean isXmlMappingEnabled() {
		return delegate.isXmlMappingEnabled();
	}

	@Override
	public boolean disallowExtensionsInCdi() {
		return delegate.disallowExtensionsInCdi();
	}
}
