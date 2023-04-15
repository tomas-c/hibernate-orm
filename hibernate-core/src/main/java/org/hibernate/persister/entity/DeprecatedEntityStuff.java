/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.persister.entity;

import org.hibernate.metadata.ClassMetadata;

/**
 * @deprecated Just used to singly extend all the deprecated entity persister roles
 */
@Deprecated
public interface DeprecatedEntityStuff
		extends OuterJoinLoadable, ClassMetadata, UniqueKeyLoadable, SQLLoadable, Lockable,	org.hibernate.persister.entity.Queryable {
}
