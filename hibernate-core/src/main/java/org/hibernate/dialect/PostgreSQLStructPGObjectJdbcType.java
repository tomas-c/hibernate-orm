/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.UnknownBasicJavaType;
import org.hibernate.type.descriptor.jdbc.AggregateJdbcType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;

import org.postgresql.util.PGobject;

/**
 * @author Christian Beikov
 */
public class PostgreSQLStructPGObjectJdbcType extends AbstractPostgreSQLStructJdbcType {

	private final ValueExtractor<Object[]> objectArrayExtractor;

	public PostgreSQLStructPGObjectJdbcType() {
		this( null, null, null );
	}

	private PostgreSQLStructPGObjectJdbcType(
			EmbeddableMappingType embeddableMappingType,
			String typeName,
			int[] orderMapping) {
		super( embeddableMappingType, typeName, orderMapping );
		this.objectArrayExtractor = super.getExtractor( new UnknownBasicJavaType<>( Object[].class ) );
	}

	@Override
	public AggregateJdbcType resolveAggregateJdbcType(
			EmbeddableMappingType mappingType,
			String sqlType,
			RuntimeModelCreationContext creationContext) {
		return new PostgreSQLStructPGObjectJdbcType(
				mappingType,
				sqlType,
				creationContext.getBootModel()
						.getDatabase()
						.getDefaultNamespace()
						.locateUserDefinedType( Identifier.toIdentifier( sqlType ) )
						.getOrderMapping()
		);
	}

	@Override
	public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
		return new BasicBinder<>( javaType, this ) {
			@Override
			protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options)
					throws SQLException {
				final String stringValue = ( (AbstractPostgreSQLStructJdbcType) getJdbcType() ).toString(
						value,
						getJavaType(),
						options
				);
				final PGobject holder = new PGobject();
				holder.setType( getTypeName() );
				holder.setValue( stringValue );
				st.setObject( index, holder );
			}

			@Override
			protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
					throws SQLException {
				final String stringValue = ( (AbstractPostgreSQLStructJdbcType) getJdbcType() ).toString(
						value,
						getJavaType(),
						options
				);
				final PGobject holder = new PGobject();
				holder.setType( getTypeName() );
				holder.setValue( stringValue );
				st.setObject( name, holder );
			}
		};
	}

	@Override
	public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
		if ( javaType.getJavaTypeClass() == Object[].class ) {
			//noinspection unchecked
			return (ValueExtractor<X>) objectArrayExtractor;
		}
		return super.getExtractor( javaType );
	}
}
