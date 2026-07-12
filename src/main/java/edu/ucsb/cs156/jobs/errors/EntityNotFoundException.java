package edu.ucsb.cs156.jobs.errors;

/**
 * RuntimeException indicating that an entity of a specific type with a given id was not found.
 * Mapped to a 404 by {@link edu.ucsb.cs156.jobs.controllers.JobsController}.
 */
public class EntityNotFoundException extends RuntimeException {
  /**
   * Constructor for the exception
   *
   * @param entityType The class of the entity that was not found, e.g. Job.class
   * @param id the id that was being searched for
   */
  public EntityNotFoundException(Class<?> entityType, Object id) {
    super("%s with id %s not found".formatted(entityType.getSimpleName(), id.toString()));
  }
}
