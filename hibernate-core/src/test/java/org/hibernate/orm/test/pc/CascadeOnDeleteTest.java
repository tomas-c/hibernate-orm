/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.pc;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.orm.test.jpa.BaseEntityManagerFunctionalTestCase;

import org.hibernate.testing.DialectChecks;
import org.hibernate.testing.RequiresDialectFeature;
import org.junit.Test;

import static jakarta.persistence.FetchType.LAZY;
import static org.hibernate.annotations.OnDeleteAction.CASCADE;
import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;

/**
 * @author Vlad Mihalcea
 */
@RequiresDialectFeature(DialectChecks.SupportsCascadeDeleteCheck.class)
public class CascadeOnDeleteTest extends BaseEntityManagerFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
			Person.class,
			Phone.class
		};
	}

	@Test
	public void test() {
		doInJPA(this::entityManagerFactory, entityManager -> {
			Person person = new Person();
			person.setId(1L);
			person.setName("John Doe");
			entityManager.persist(person);

			Phone phone = new Phone();
			phone.setId(1L);
			phone.setNumber("123-456-7890");
			phone.setOwner(person);
			entityManager.persist(phone);
		});

		doInJPA(this::entityManagerFactory, entityManager -> {
			//tag::pc-cascade-on-delete-example[]
			Person person = entityManager.find(Person.class, 1L);
			entityManager.remove(person);
			//end::pc-cascade-on-delete-example[]
		});
	}

	//tag::pc-cascade-on-delete-mapping-Person-example[]
	@Entity(name = "Person")
	public static class Person {

		@Id
		private Long id;

		private String name;
		
		//Getters and setters are omitted for brevity

	//end::pc-cascade-on-delete-mapping-Person-example[]

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	//tag::pc-cascade-on-delete-mapping-Person-example[]
	}
	//end::pc-cascade-on-delete-mapping-Person-example[]

	//tag::pc-cascade-on-delete-mapping-Phone-example[]
	@Entity(name = "Phone")
	public static class Phone {

		@Id
		private Long id;

		@Column(name = "`number`")
		private String number;

		@ManyToOne(fetch = LAZY)
		@OnDelete(action = CASCADE)
		private Person owner;

		//Getters and setters are omitted for brevity

	//end::pc-cascade-on-delete-mapping-Phone-example[]
		
		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getNumber() {
			return number;
		}

		public void setNumber(String number) {
			this.number = number;
		}

		public Person getOwner() {
			return owner;
		}

		public void setOwner(Person owner) {
			this.owner = owner;
		}
	//tag::pc-cascade-on-delete-mapping-Phone-example[]
	}
	//end::pc-cascade-on-delete-mapping-Phone-example[]
}
