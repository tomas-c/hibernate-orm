/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.annotations.enumerated;

import java.sql.Types;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.type.ConvertedBasicType;
import org.hibernate.type.descriptor.converter.internal.NamedEnumValueConverter;
import org.hibernate.type.descriptor.converter.internal.OrdinalEnumValueConverter;
import org.hibernate.type.descriptor.converter.spi.EnumValueConverter;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import org.assertj.core.api.Assert;
import org.assertj.core.api.Assertions;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author Steve Ebersole
 */
@ServiceRegistry
public class EnumeratedSmokeTest {
	/**
	 * I personally have been unable to repeoduce the bug as reported in HHH-10402.  This test
	 * is equivalent to what the reporters say happens, but these tests pass fine.
	 */
	@Test
	@JiraKey( "HHH-10402" )
	public void testEnumeratedTypeResolutions(ServiceRegistryScope serviceRegistryScope) {
		final MetadataImplementor mappings = (MetadataImplementor) new MetadataSources( serviceRegistryScope.getRegistry() )
				.addAnnotatedClass( EntityWithEnumeratedAttributes.class )
				.buildMetadata();
		mappings.orderColumns( false );
		mappings.validate();

		final JdbcTypeRegistry jdbcTypeRegistry = mappings.getTypeConfiguration().getJdbcTypeRegistry();
		final PersistentClass entityBinding = mappings.getEntityBinding( EntityWithEnumeratedAttributes.class.getName() );

		validateEnumMapping( jdbcTypeRegistry, entityBinding.getProperty( "notAnnotated" ), EnumType.ORDINAL );
		validateEnumMapping( jdbcTypeRegistry, entityBinding.getProperty( "noEnumType" ), EnumType.ORDINAL );
		validateEnumMapping( jdbcTypeRegistry, entityBinding.getProperty( "ordinalEnumType" ), EnumType.ORDINAL );
		validateEnumMapping( jdbcTypeRegistry, entityBinding.getProperty( "stringEnumType" ), EnumType.STRING );
	}

	private void validateEnumMapping(JdbcTypeRegistry jdbcRegistry, Property property, EnumType expectedJpaEnumType) {
		final ConvertedBasicType<?> propertyType = (ConvertedBasicType<?>) property.getType();
		final EnumValueConverter<?, ?> valueConverter = (EnumValueConverter<?, ?>) propertyType.getValueConverter();
		final JdbcMapping jdbcMapping = propertyType.getJdbcMapping();
		final JdbcType jdbcType = jdbcMapping.getJdbcType();

		assert expectedJpaEnumType != null;
		if ( expectedJpaEnumType == EnumType.ORDINAL ) {
			Assertions.assertThat( valueConverter ).isInstanceOf( OrdinalEnumValueConverter.class );
			Assertions.assertThat( jdbcType.isInteger() ).isTrue();
		}
		else {
			Assertions.assertThat( valueConverter ).isInstanceOf( NamedEnumValueConverter.class );
			Assertions.assertThat( jdbcType.isString() ).isTrue();
		}
	}

	@Entity
	public static class EntityWithEnumeratedAttributes {
		@Id
		public Integer id;
		public Gender notAnnotated;
		@Enumerated
		public Gender noEnumType;
		@Enumerated(EnumType.ORDINAL)
		public Gender ordinalEnumType;
		@Enumerated(EnumType.STRING)
		public Gender stringEnumType;
	}

	public static enum Gender {
		MALE, FEMALE, UNKNOWN;
	}
}
