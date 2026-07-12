package edu.ucsb.cs156.jobs.services;

/**
 * Bridge between the library and the consuming app's notion of "current user". Each app implements
 * this as a bean (typically a 5-line wrapper over its existing {@code CurrentUserService}); the
 * library uses it to stamp {@code createdById}/{@code createdByEmail} on new jobs.
 */
public interface JobUserProvider {
  Long getCurrentUserId();

  String getCurrentUserEmail();
}
