INSERT INTO env_var_definitions (name, description, category, platform, local_file_location, production_location, required, example_value) VALUES
('VITE_API_BASE_URL', 'The base URL for your backend API that the frontend will call', 'PUBLIC_FRONTEND', 'netlify', 'frontend/.env.local', 'Netlify Environment Variables', true, 'https://my-api.onrender.com/api'),
('VITE_APP_NAME', 'The display name of your application shown in the frontend', 'PUBLIC_FRONTEND', 'netlify', 'frontend/.env.local', 'Netlify Environment Variables', false, 'MyApp'),
('VITE_SUPABASE_URL', 'Your Supabase project URL for database and auth connections', 'PUBLIC_FRONTEND', 'netlify', 'frontend/.env.local', 'Netlify Environment Variables', false, 'https://abcdefgh123456.supabase.co'),
('VITE_SUPABASE_ANON_KEY', 'The public anonymous key for Supabase client-side connections', 'PUBLIC_FRONTEND', 'netlify', 'frontend/.env.local', 'Netlify Environment Variables', false, 'eyJhbGci...'),
('VITE_FIREBASE_API_KEY', 'Your Firebase project API key for client SDK initialization', 'PUBLIC_FRONTEND', 'netlify', 'frontend/.env.local', 'Netlify Environment Variables', false, 'AIzaSyC...'),
('VITE_FIREBASE_PROJECT_ID', 'Your Firebase project identifier', 'PUBLIC_FRONTEND', 'netlify', 'frontend/.env.local', 'Netlify Environment Variables', false, 'my-project-123'),
('GEMINI_API_KEY', 'API key for Google Gemini AI integration', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', false, 'AIzaSyB...'),
('OPENAI_API_KEY', 'API key for OpenAI integration', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', false, 'sk-proj-...'),
('DATABASE_URL', 'JDBC connection URL for PostgreSQL database', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', true, 'jdbc:postgresql://host:5432/db?sslmode=require'),
('DATABASE_USERNAME', 'PostgreSQL database username', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', true, 'postgres'),
('DATABASE_PASSWORD', 'PostgreSQL database password', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', true, '[your-password]'),
('JWT_SECRET', 'Secret key for signing JWT authentication tokens', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', true, '[256-bit-secret]'),
('SUPABASE_SERVICE_ROLE_KEY', 'Service role key that bypasses Row Level Security', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', false, 'eyJhbGci...'),
('FIREBASE_PRIVATE_KEY', 'Firebase Admin SDK private key for server-side operations', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', false, '-----BEGIN PRIVATE KEY-----...'),
('FIREBASE_CLIENT_EMAIL', 'Firebase Admin SDK client email', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', false, 'firebase-adminsdk@project.iam.gserviceaccount.com'),
('SPRING_PROFILES_ACTIVE', 'Which Spring Boot profile to use (dev, prod)', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', true, 'prod'),
('FRONTEND_URL', 'The deployed frontend URL for CORS configuration', 'PRIVATE_BACKEND', 'render', 'backend .env or Render env vars', 'Render Environment Variables', true, 'https://my-app.netlify.app')
ON CONFLICT DO NOTHING;
