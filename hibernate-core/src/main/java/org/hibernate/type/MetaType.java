/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.metamodel.mapping.EntityDiscriminatorMapping;
import org.hibernate.metamodel.mapping.DiscriminatorConverter;
import org.hibernate.persister.entity.DiscriminatorMetadata;
import org.hibernate.persister.entity.DiscriminatorType;

/**
 * @author Gavin King
 *
 * @deprecated The functionality of MetaType, {@link DiscriminatorType} and {@link DiscriminatorMetadata}  have been
 * consolidated into {@link EntityDiscriminatorMapping} and {@link DiscriminatorConverter}
 */
@Deprecated( since = "6.2", forRemoval = true )
public class MetaType extends AbstractType {
	public static final String[] REGISTRATION_KEYS = ArrayHelper.EMPTY_STRING_ARRAY;

	private final Type baseType;
	private final Map<Object,String> discriminatorValuesToEntityNameMap;
	private final Map<String,Object> entityNameToDiscriminatorValueMap;

	public MetaType(Map<Object,String> discriminatorValuesToEntityNameMap, Type baseType) {
		this.baseType = baseType;
		this.discriminatorValuesToEntityNameMap = discriminatorValuesToEntityNameMap;
		this.entityNameToDiscriminatorValueMap = new HashMap<>();
		for ( Map.Entry<Object,String> entry : discriminatorValuesToEntityNameMap.entrySet() ) {
			entityNameToDiscriminatorValueMap.put( entry.getValue(), entry.getKey() );
		}
	}

	public Type getBaseType() {
		return baseType;
	}

	public String[] getRegistrationKeys() {
		return REGISTRATION_KEYS;
	}

	public Map<Object, String> getDiscriminatorValuesToEntityNameMap() {
		return discriminatorValuesToEntityNameMap;
	}

	public Map<String,Object> getEntityNameToDiscriminatorValueMap(){
		return entityNameToDiscriminatorValueMap;
	}

	public int[] getSqlTypeCodes(Mapping mapping) throws MappingException {
		return baseType.getSqlTypeCodes(mapping);
	}

	@Override
	public int getColumnSpan(Mapping mapping) throws MappingException {
		return baseType.getColumnSpan(mapping);
	}

	@Override
	public Class<?> getReturnedClass() {
		return String.class;
	}

	@Override
	public int compare(Object x, Object y, SessionFactoryImplementor sessionFactory) {
		return compare( x, y );
	}

	@Override
	public void nullSafeSet(
			PreparedStatement st,
			Object value,
			int index,
			SharedSessionContractImplementor session) throws HibernateException, SQLException {
		baseType.nullSafeSet(st, value==null ? null : entityNameToDiscriminatorValueMap.get(value), index, session);
	}

	@Override
	public void nullSafeSet(
			PreparedStatement st,
			Object value,
			int index,
			boolean[] settable,
			SharedSessionContractImplementor session) throws HibernateException, SQLException {
		if ( settable[0] ) {
			nullSafeSet(st, value, index, session);
		}
	}

	@Override
	public String toLoggableString(Object value, SessionFactoryImplementor factory) throws HibernateException {
		return toXMLString(value, factory);
	}
	
	public String toXMLString(Object value, SessionFactoryImplementor factory) throws HibernateException {
		return (String) value; //value is the entity name
	}

	public Object fromXMLString(String xml, Mapping factory) throws HibernateException {
		return xml; //xml is the entity name
	}

	@Override
	public String getName() {
		return baseType.getName(); //TODO!
	}

	@Override
	public Object deepCopy(Object value, SessionFactoryImplementor factory) throws HibernateException {
		return value;
	}

	@Override
	public Object replace(
			Object original,
			Object target,
			SharedSessionContractImplementor session,
			Object owner,
			Map<Object, Object> copyCache) {
		return original;
	}

	@Override
	public boolean isMutable() {
		return false;
	}

	@Override
	public boolean[] toColumnNullness(Object value, Mapping mapping) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isDirty(Object old, Object current, boolean[] checkable, SharedSessionContractImplementor session) throws HibernateException {
		return checkable[0] && isDirty(old, current, session);
	}
}
