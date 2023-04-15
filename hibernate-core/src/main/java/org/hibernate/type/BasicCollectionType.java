/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import java.util.Collection;

import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.BasicCollectionJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;

/**
 * A type that maps between {@link java.sql.Types#ARRAY ARRAY} and {@code Collection<T>}
 *
 * @author Christian Beikov
 */
public class BasicCollectionType<C extends Collection<E>, E>
		extends AbstractSingleColumnStandardBasicType<C>
		implements AdjustableBasicType<C>, BasicPluralType<C, E> {

	private final BasicType<E> baseDescriptor;
	private final String name;

	public BasicCollectionType(
			BasicType<E> baseDescriptor,
			JdbcType arrayJdbcType,
			BasicCollectionJavaType<C, E> collectionTypeDescriptor) {
		super( arrayJdbcType, collectionTypeDescriptor );
		this.baseDescriptor = baseDescriptor;
		this.name = determineName( collectionTypeDescriptor, baseDescriptor );
	}

	private static String determineName(BasicCollectionJavaType<?, ?> collectionTypeDescriptor, BasicType<?> baseDescriptor) {
		switch ( collectionTypeDescriptor.getSemantics().getCollectionClassification() ) {
			case BAG:
			case ID_BAG:
				return "Collection<" + baseDescriptor.getName() + ">";
			case LIST:
				return "List<" + baseDescriptor.getName() + ">";
			case SET:
				return "Set<" + baseDescriptor.getName() + ">";
			case SORTED_SET:
				return "SortedSet<" + baseDescriptor.getName() + ">";
			case ORDERED_SET:
				return "OrderedSet<" + baseDescriptor.getName() + ">";
		}
		return null;
	}

	@Override
	public BasicType<E> getElementType() {
		return baseDescriptor;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	protected boolean registerUnderJavaType() {
		return true;
	}

	@Override
	public <X> BasicType<X> resolveIndicatedType(JdbcTypeIndicators indicators, JavaType<X> domainJtd) {
		// TODO: maybe fallback to some encoding by default if the DB doesn't support arrays natively?
		//  also, maybe move that logic into the ArrayJdbcType
		//noinspection unchecked
		return (BasicType<X>) this;
	}
}
