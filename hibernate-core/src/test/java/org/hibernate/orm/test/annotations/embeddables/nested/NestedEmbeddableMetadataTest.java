/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.annotations.embeddables.nested;

import java.sql.Types;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Value;
import org.hibernate.type.spi.TypeConfiguration;

import org.junit.jupiter.api.Test;

import static org.hibernate.testing.junit4.ExtraAssertions.assertJdbcTypeCode;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Steve Ebersole
 */
public class NestedEmbeddableMetadataTest {

	@Test
	public void testEnumTypeInterpretation() {
		final StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
				.enableAutoClose()
				.applySetting( AvailableSettings.HBM2DDL_AUTO, "create-drop" )
				.build();

		try {
			final Metadata metadata = new MetadataSources( serviceRegistry )
					.addAnnotatedClass( Customer.class )
					.buildMetadata();
			final TypeConfiguration typeConfiguration = metadata.getDatabase().getTypeConfiguration();

			PersistentClass classMetadata = metadata.getEntityBinding( Customer.class.getName() );
			Property investmentsProperty = classMetadata.getProperty( "investments" );
			Collection investmentsValue = (Collection) investmentsProperty.getValue();
			Component investmentMetadata = (Component) investmentsValue.getElement();
			Value descriptionValue = investmentMetadata.getProperty( "description" ).getValue();
			assertEquals( 1, descriptionValue.getColumnSpan() );
			Column selectable = (Column) descriptionValue.getColumnIterator().next();
			assertEquals( (Long) 500L, selectable.getLength() );
			Component amountMetadata = (Component) investmentMetadata.getProperty( "amount" ).getValue();
			SimpleValue currencyMetadata = (SimpleValue) amountMetadata.getProperty( "currency" ).getValue();
			int[] currencySqlTypes = currencyMetadata.getType().getSqlTypeCodes( metadata );
			assertEquals( 1, currencySqlTypes.length );
			assertJdbcTypeCode(
					typeConfiguration.getJdbcTypeRegistry().getDescriptor( Types.VARCHAR ).getJdbcTypeCode(),
					currencySqlTypes[0]
			);
		}
		finally {
			StandardServiceRegistryBuilder.destroy( serviceRegistry );
		}
	}
}
