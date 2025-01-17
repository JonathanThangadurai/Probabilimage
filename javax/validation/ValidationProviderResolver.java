package javax.validation;

import java.util.List;
import javax.validation.spi.ValidationProvider;

public interface ValidationProviderResolver {
    List<ValidationProvider<?>> getValidationProviders();
}
