package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductDTO;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;
	private final DiscountStrategyFactory discountStrategyFactory;

    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll()
                .stream().map(ProductDTO::from).collect(Collectors.toList());
    }

    public ProductDTO getProductById(Long id) {
        return productRepository.findById(id)
                .map(ProductDTO::from)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + id));
    }

    public ProductDTO createProduct(Product product) {
        return ProductDTO.from(productRepository.save(product));
    }

    public ProductDTO updateProduct(Long id, Product updated) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + id));
        existing.setName(updated.getName());
        existing.setPrice(updated.getPrice());
        existing.setStock(updated.getStock());
        existing.setDescription(updated.getDescription());
        return ProductDTO.from(productRepository.save(existing));
    }

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id))
            throw new RuntimeException("Producto no encontrado: " + id);
        productRepository.deleteById(id);
    }

    public double calculateDiscountedPrice(Long productId, String userType) {
        ProductDTO product = getProductById(productId);
        DiscountStrategy strategy = discountStrategyFactory.getStrategy(userType);
        return strategy.apply(product.getPrice());
    }
   
}
