package org.hibernate.orm.test.batch;


import java.util.ArrayList;
import java.util.List;

import org.hibernate.cfg.AvailableSettings;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DomainModel(
		annotatedClasses = {
				BatchUpdateAndVersionTest.EntityA.class
		}
)
@SessionFactory
@ServiceRegistry(
		settings = @Setting(name = AvailableSettings.STATEMENT_BATCH_SIZE, value = "2")
)
@TestForIssue(jiraKey = "HHH-16394")
public class BatchUpdateAndVersionTest {

	@Test
	public void testUpdate(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					EntityA entityA1 = new EntityA( 1 );

					EntityA entityA2 = new EntityA( 2 );

					EntityA ownerA2 = new EntityA( 3 );

					session.persist( ownerA2 );
					session.persist( entityA1 );
					session.persist( entityA2 );

					session.flush();

					entityA1.setPropertyA( 3 );
					entityA2.addOwner( ownerA2 );
				}
		);

		scope.inTransaction(
				session -> {
					EntityA entityA1 = session.get( EntityA.class, 1 );
					assertThat( entityA1.getVersion() ).isEqualTo( 1 );
					assertThat( entityA1.getPropertyA() ).isEqualTo( 3 );
					assertThat( entityA1.getOwners().size() ).isEqualTo( 0 );

					EntityA entityA2 = session.get( EntityA.class, 2 );
					assertThat( entityA2.getVersion() ).isEqualTo( 1 );
					assertThat( entityA2.getPropertyA() ).isEqualTo( 0 );

					List<EntityA> owners = entityA2.getOwners();
					assertThat( owners.size() ).isEqualTo( 1 );

					EntityA ownerA2 = owners.get( 0 );
					assertThat( ownerA2.getId() ).isEqualTo( 3 );
					assertThat( ownerA2.getPropertyA() ).isEqualTo( 0 );
					assertThat( ownerA2.getOwners().size() ).isEqualTo( 0 );
				}
		);
	}

	@Entity(name = "EntityA")
	@Table(name = "ENTITY_A")
	public static class EntityA {

		@Id
		@Column(name = "ID")
		Integer id;

		@Version
		@Column(name = "VERSION")
		long version;

		@Column(name = "PROPERTY_A")
		int propertyA;

		@ManyToMany
		@JoinTable(name = "ENTITY_A_TO_ENTITY_A", inverseJoinColumns = @JoinColumn(name = "SIDE_B"), joinColumns = @JoinColumn(name = "SIDE_A"))
		final List<EntityA> owners = new ArrayList<>();

		public EntityA() {
		}

		public EntityA(Integer id) {
			this.id = id;
		}

		public void setPropertyA(int propertyA) {
			this.propertyA = propertyA;
		}

		public void addOwner(EntityA owner) {
			owners.add( owner );
		}

		public Integer getId() {
			return id;
		}

		public long getVersion() {
			return version;
		}

		public int getPropertyA() {
			return propertyA;
		}

		public List<EntityA> getOwners() {
			return owners;
		}
	}

}
