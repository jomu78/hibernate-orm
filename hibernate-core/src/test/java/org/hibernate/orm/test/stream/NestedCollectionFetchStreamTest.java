/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.stream;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hibernate.dialect.DB2Dialect;
import org.hibernate.dialect.DerbyDialect;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.Jira;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SkipForDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Tuple;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marco Belladelli
 */
@DomainModel( annotatedClasses = {
		NestedCollectionFetchStreamTest.BasicEntity.class,
		NestedCollectionFetchStreamTest.EntityA.class,
		NestedCollectionFetchStreamTest.EntityB.class,
		NestedCollectionFetchStreamTest.EntityC.class,
} )
@SessionFactory
@Jira( "https://hibernate.atlassian.net/browse/HHH-17131" )
@SkipForDialect( dialectClass = DB2Dialect.class, reason = "The support for ResultSet#isBeforeFirst() is optional for ResultSets with a result set type of TYPE_FORWARD_ONLY and DB2 does not support it" )
@SkipForDialect( dialectClass = DerbyDialect.class, reason = "The support for ResultSet#isBeforeFirst() is optional for ResultSets with a result set type of TYPE_FORWARD_ONLY and Derby does not support it" )
public class NestedCollectionFetchStreamTest {
	@BeforeAll
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final EntityB b = new EntityB();
			b.getC().addAll( Stream.generate( EntityC::new ).limit( 3 ).collect( Collectors.toSet() ) );
			session.persist( b );
			session.persist( new EntityA( b ) );
		} );
	}

	@AfterAll
	public void tearDown(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			session.createMutationQuery( "delete from EntityA" ).executeUpdate();
			session.createMutationQuery( "delete from EntityC" ).executeUpdate();
			session.createMutationQuery( "delete from EntityB" ).executeUpdate();
		} );
	}

	@Test
	public void testImplicitNestedCollection(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			try (final Stream<EntityA> stream = session.createQuery(
					"select a from EntityA a left join fetch a.b b left join fetch b.c c",
					EntityA.class
			).getResultStream()) {
				final List<EntityA> list = stream.collect( Collectors.toList() );
				assertThat( list ).hasSize( 1 );
				assertThat( list.get( 0 ).getB() ).isNotNull();
				assertThat( list.get( 0 ).getB().getC() ).hasSize( 3 );
			}
		} );
	}

	@Test
	public void testExplicitNestedCollection(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			try (final Stream<Tuple> stream = session.createQuery(
					"select a, b from EntityA a left join fetch a.b b left join fetch b.c c",
					Tuple.class
			).getResultStream()) {
				final List<Tuple> list = stream.collect( Collectors.toList() );
				assertThat( list ).hasSize( 1 );
				assertThat( list.get( 0 ).get( 1, EntityB.class ) ).isNotNull();
				assertThat( list.get( 0 ).get( 1, EntityB.class ).getC() ).hasSize( 3 );
			}
		} );
	}

	@MappedSuperclass
	public static abstract class BasicEntity {
		@Id
		@GeneratedValue
		private Long id;
	}

	@Entity( name = "EntityA" )
	public static class EntityA extends BasicEntity {
		@ManyToOne
		private EntityB b;

		public EntityA() {
		}

		public EntityA(EntityB b) {
			this.b = b;
		}

		public EntityB getB() {
			return b;
		}
	}

	@Entity( name = "EntityB" )
	public static class EntityB extends BasicEntity {
		@OneToMany( cascade = CascadeType.PERSIST )
		@JoinColumn( name = "entityb_id" )
		private Set<EntityC> c = new HashSet<>();

		public Set<EntityC> getC() {
			return c;
		}
	}

	@Entity( name = "EntityC" )
	public static class EntityC extends BasicEntity {
	}
}
