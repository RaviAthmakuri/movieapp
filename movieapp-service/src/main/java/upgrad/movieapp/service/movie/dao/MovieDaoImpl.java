package upgrad.movieapp.service.movie.dao;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.ListJoin;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import upgrad.movieapp.service.common.dao.BaseDaoImpl;
import upgrad.movieapp.service.common.model.SearchResult;
import upgrad.movieapp.service.movie.entity.GenreEntity_;
import upgrad.movieapp.service.movie.entity.MovieEntity;
import upgrad.movieapp.service.movie.entity.MovieEntity_;
import upgrad.movieapp.service.movie.entity.MovieGenreEntity;
import upgrad.movieapp.service.movie.entity.MovieGenreEntity_;
import upgrad.movieapp.service.movie.model.MovieSearchQuery;
import upgrad.movieapp.service.movie.model.MovieSortBy;
import upgrad.movieapp.service.movie.model.MovieStatus;

@Repository
public class MovieDaoImpl extends BaseDaoImpl<MovieEntity> implements MovieDao {

    @Override
    public SearchResult<MovieEntity> findMovies(final MovieSearchQuery searchQuery) {

        final CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<MovieEntity> payloadQuery = builder.createQuery(MovieEntity.class);
        final Root<MovieEntity> from = payloadQuery.from(MovieEntity.class);

        final Predicate[] payloadPredicates = buildPredicates(searchQuery, builder, from);

        payloadQuery.select(from).where(payloadPredicates);

        Set<MovieSortBy> sortBy = searchQuery.getSortBy();
        if (CollectionUtils.isNotEmpty(sortBy)) {
            final List<Order> orderList = new ArrayList();
            if (sortBy.contains(MovieSortBy.RATING)) {
                orderList.add(builder.desc(from.get(MovieEntity_.rating)));
            }
            if (sortBy.contains(MovieSortBy.RELEASE_DATE)) {
                orderList.add(builder.desc(from.get(MovieEntity_.releaseAt)));
            }
            payloadQuery.orderBy(orderList);
        }

        List<MovieEntity> payload = entityManager.createQuery(payloadQuery).getResultList();

        final CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        final Root<MovieEntity> countFrom = countQuery.from(MovieEntity.class);

        final Predicate[] countPredicates = buildPredicates(searchQuery, builder, countFrom);
        countQuery.select(builder.count(countFrom)).where(countPredicates);
        final Integer totalCount = entityManager.createQuery(countQuery).getSingleResult().intValue();

        return new SearchResult(totalCount, payload);
    }

    private Set<String> movieStatusNames(final MovieStatus... movieStatuses) {
        final Set<String> movieStatusNames = new HashSet<>();
        for (MovieStatus movieStatus : movieStatuses) {
            movieStatusNames.add(movieStatus.name());
        }
        return movieStatusNames;
    }

    private Predicate[] buildPredicates(final MovieSearchQuery searchQuery, final CriteriaBuilder builder, final Root<MovieEntity> root) {

        final List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.isNotEmpty(searchQuery.getTitle())) {
            predicates.add(builder.like(builder.lower(root.get(MovieEntity_.title)), like(searchQuery.getTitle().toLowerCase())));
        }

        if (CollectionUtils.isNotEmpty(searchQuery.getStatuses())) {
            final EnumSet<MovieStatus> movieStatuses = searchQuery.getStatuses();
            final Set<String> statuses = movieStatuses.stream().map(movieStatus -> {
                return movieStatus.name();
            }).collect(Collectors.toSet());

            predicates.add(root.get(MovieEntity_.status).in(statuses));
        }

        final Set<String> genres = searchQuery.getGenres();
        if (CollectionUtils.isNotEmpty(genres)) {
            final ListJoin<MovieEntity, MovieGenreEntity> join = root.join(MovieEntity_.genres);

            Set<String> genresLowerCase = genres.stream().map(genre -> {
                return genre.toLowerCase();
            }).collect(Collectors.toSet());
            predicates.add(builder.lower(join.get(MovieGenreEntity_.genre).get(GenreEntity_.genre)).in(genresLowerCase));
        }

        if (searchQuery.getReleaseDateFrom() != null) {
            final ZonedDateTime releaseDateFrom = searchQuery.getReleaseDateFrom().withHour(0).withMinute(0).withSecond(0);
            final Expression<ZonedDateTime> releaseAt = root.get(MovieEntity_.releaseAt);
            predicates.add(builder.greaterThanOrEqualTo(releaseAt, releaseDateFrom));
        }

        if (searchQuery.getReleaseDateTo() != null) {
            final ZonedDateTime releaseDateTo = searchQuery.getReleaseDateTo().withHour(23).withMinute(59).withSecond(59);
            final Expression<ZonedDateTime> releaseAt = root.get(MovieEntity_.releaseAt);
            predicates.add(builder.lessThanOrEqualTo(releaseAt, releaseDateTo));
        }

        if (searchQuery.getRatingMin() != null) {
            final Expression<Float> rating = root.get(MovieEntity_.rating);
            predicates.add(builder.greaterThanOrEqualTo(rating, searchQuery.getRatingMin()));
        }

        if (searchQuery.getRatingMax() != null) {
            final Expression<Float> rating = root.get(MovieEntity_.rating);
            predicates.add(builder.lessThanOrEqualTo(rating, searchQuery.getRatingMax()));
        }

        return predicates.toArray(new Predicate[]{});
    }

    private String like(final String text) {
        return "%" + text + "%";
    }

}
