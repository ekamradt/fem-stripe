package ah.customer.stripe.controller;

import ah.config.StripeConfig;
import ah.helper.StripeHelper;
import ah.rest.AhResponse;
import com.stripe.Stripe;
import com.stripe.model.Product;
import com.stripe.model.ProductCollection;
import com.stripe.net.StripeResponse;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.ProductListParams;
import com.stripe.param.ProductUpdateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static ah.helper.AhConstant.STRIPE_REST_LARGE_LIMIT;
import static ah.helper.StripeRequestHelper.ahResponseError;

@RestController
@RequestMapping("/api/v1/")
@Slf4j
public class StripeControllerProduct {

    @Autowired
    public StripeControllerProduct(StripeConfig config) {
        Stripe.apiKey = config.stripeSecretKey();
    }

    @GetMapping("/products/all")
    public ResponseEntity<AhResponse<Product>> getProducts() {
        return getProducts(STRIPE_REST_LARGE_LIMIT);
    }

    @GetMapping("/products")
    public ResponseEntity<AhResponse<Product>> getProducts(@RequestBody String productListParamsString) {
        try {
            final ProductListParams productListParams = StripeHelper.getGson().fromJson(productListParamsString, ProductListParams.class);
            final ProductCollection productCollection = Product.list(productListParams);

            final StripeResponse lastResponse = productCollection.getLastResponse();
            if (lastResponse.code() == HttpStatus.OK.value()) {
                return AhResponse.buildOk(productCollection.getData());
            }
            final String errMsg = String.format("Error getting products : Code %d \n%s", lastResponse.code(),
                    StripeHelper.objectToJson(productCollection));
            log.error(errMsg);
            return AhResponse.internalError(errMsg);

        } catch (Exception e) {
            log.error("Error Fetching Product.", e);
            return AhResponse.internalError(e);
        }
    }

    @GetMapping("/product/{id}")
    public ResponseEntity<AhResponse<Product>> getProduct(@PathVariable("id") String productCid) {
        try {
            final Product product = Product.retrieve(productCid);
            return buildStripeResponseProduct(product, "Error fetching Product");
        } catch (Exception e) {
            log.error("Error Fetching Product.", e);
            return AhResponse.internalError(e);
        }
    }

    @PostMapping("/product")
    public ResponseEntity<AhResponse<Product>> createProduct(@RequestBody String productCreateParamString) {
        try {
            final ProductCreateParams productCreateParams = StripeHelper.getGson().fromJson(productCreateParamString, ProductCreateParams.class);
            final Product productNew = Product.create(productCreateParams);
            return buildStripeResponseProduct(productNew, "Error Creating Product");
        } catch (Exception e) {
            log.error("Error Creating Product.", e);
            return AhResponse.internalError(e);
        }
    }

    @PutMapping("/product/{id}")
    public ResponseEntity<AhResponse<Product>> updateProduct(@PathVariable("id") String productCid, @RequestBody String productUpdateParamString) {

        try {
            final ProductUpdateParams productUpdateParams = StripeHelper.getGson().fromJson(productUpdateParamString, ProductUpdateParams.class);
            final Product existingProduct = Product.retrieve(productCid);
            final Product updatedProduct = existingProduct.update(productUpdateParams);
            return buildStripeResponseProduct(updatedProduct, "Error Updating Product");
        } catch (Exception e) {
            log.error("Error Updating Product.", e);
            return AhResponse.internalError(e);
        }
    }

    @DeleteMapping("/product/{id}")
    public ResponseEntity<AhResponse<Product>> deleteProduct(@PathVariable("id") String productCid) {
        try {
            final Product product = Product.retrieve(productCid);
            final Product deletedProduct = product.delete();
            return buildStripeResponseProduct(deletedProduct, "Error Product.");
        } catch (Exception e) {
            log.error("Error Removing Product.", e);
            final String errorMessage = e.getMessage();
            if (errorMessage.contains("cannot be deleted")) {
                return AhResponse.conflictError("Product cannot be deleted, still has attached Price.", e);
            }
            return AhResponse.internalError(e);
        }
    }

    private ResponseEntity<AhResponse<Product>> buildStripeResponseProduct(Product product, String msg) {
        final StripeResponse lastResponse = product.getLastResponse();
        if (lastResponse.code() == HttpStatus.OK.value()) {
            final Product fetchedProduct = StripeHelper.jsonToObject(lastResponse.body(), Product.class);
            return AhResponse.buildOk(fetchedProduct);
        }
        return ahResponseError(msg, lastResponse.code(), product);
    }
}