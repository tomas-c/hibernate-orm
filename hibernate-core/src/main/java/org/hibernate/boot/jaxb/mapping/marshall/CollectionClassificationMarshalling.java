/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.jaxb.mapping.marshall;

import org.hibernate.metamodel.CollectionClassification;

/**
 * JAXB marshalling for {@link CollectionClassification}
 *
 * @author Steve Ebersole
 */
public class CollectionClassificationMarshalling {
	public static CollectionClassification fromXml(String name) {
		return CollectionClassification.interpretSetting( name.replace( '-', '_' ) );
	}

	public static String toXml(CollectionClassification classification) {
		return classification.name().replace( '_', '-' );
	}
}
