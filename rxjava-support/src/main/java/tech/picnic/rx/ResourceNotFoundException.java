package tech.picnic.rx;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Indicates that a requested resource was not found. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
  /** The {@link java.io.Serializable serialization} ID. */
  private static final long serialVersionUID = 1L;
}
