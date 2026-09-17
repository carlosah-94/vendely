-- ============ TABLAS ============

CREATE TABLE users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('ADMIN','CUSTOMER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE stores (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(150) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(store_id, name)
);
CREATE INDEX idx_categories_store_id ON categories(store_id);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id),
    name VARCHAR(150) NOT NULL,
    description TEXT,
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_store_id ON products(store_id);
CREATE INDEX idx_products_category_id ON products(category_id);

CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE REFERENCES products(id) ON DELETE CASCADE,
    quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(cart_id, product_id)
);
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PAID','SHIPPED','CANCELLED')),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_store_id ON orders(store_id);
CREATE INDEX idx_orders_user_id ON orders(user_id);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    method VARCHAR(30) NOT NULL DEFAULT 'CARD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','COMPLETED','FAILED','REFUNDED')),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ SEED DATA ============
-- Password del admin en texto plano: Admin123!  (el hash de abajo corresponde a esa clave)

INSERT INTO users (id, full_name, email, password_hash, role) VALUES
('11111111-1111-1111-1111-111111111111', 'Admin Tienda', 'admin@shopify.test',
 '$2a$10$DowQeGRZ4nURYD2yFB.d0.YCz2Do9RaB6QUvSjA2wSCJPtQGwqZ9O', 'ADMIN'),
('22222222-2222-2222-2222-222222222222', 'Cliente Demo', 'cliente@shopify.test',
 '$2a$10$DowQeGRZ4nURYD2yFB.d0.YCz2Do9RaB6QUvSjA2wSCJPtQGwqZ9O', 'CUSTOMER');

INSERT INTO stores (id, owner_id, name, slug, description) VALUES
('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',
 'Mi Tienda Demo', 'mi-tienda-demo', 'Tienda de ejemplo para pruebas');

INSERT INTO categories (id, store_id, name) VALUES
('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', 'Electrónica'),
('55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 'Ropa');

INSERT INTO products (id, store_id, category_id, name, description, price) VALUES
('66666666-6666-6666-6666-666666666666', '33333333-3333-3333-3333-333333333333',
 '44444444-4444-4444-4444-444444444444', 'Audífonos Bluetooth', 'Audífonos inalámbricos', 79.90),
('77777777-7777-7777-7777-777777777777', '33333333-3333-3333-3333-333333333333',
 '55555555-5555-5555-5555-555555555555', 'Polo Básico', 'Polo de algodón', 29.90);

INSERT INTO inventory (id, product_id, quantity) VALUES
('88888888-8888-8888-8888-888888888888', '66666666-6666-6666-6666-666666666666', 50),
('99999999-9999-9999-9999-999999999999', '77777777-7777-7777-7777-777777777777', 100);