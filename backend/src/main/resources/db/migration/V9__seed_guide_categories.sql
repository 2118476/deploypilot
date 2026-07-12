INSERT INTO guide_categories (name, slug, description, icon, sort_order) VALUES
('GitHub', 'github', 'Repository hosting, collaboration, and CI/CD with GitHub', 'github', 1),
('Git Commands', 'git-commands', 'Essential Git commands explained with examples', 'git-branch', 2),
('Netlify', 'netlify', 'Deploy React and static frontends to Netlify', 'globe', 3),
('Render', 'render', 'Deploy Spring Boot and backend services to Render', 'server', 4),
('Supabase', 'supabase', 'PostgreSQL database, auth, and storage with Supabase', 'database', 5),
('Firebase', 'firebase', 'Authentication, notifications, and hosting with Firebase', 'flame', 6),
('Environment Variables', 'environment-variables', 'Secure configuration for all platforms', 'shield', 7),
('AI APIs', 'ai-apis', 'Integrate Gemini and OpenAI securely in your applications', 'bot', 8),
('Security', 'security', 'Best practices for securing your deployment', 'lock', 9),
('CORS', 'cors', 'Cross-Origin Resource Sharing configuration', 'link', 10),
('Custom Domains', 'custom-domains', 'Configure custom domains for your deployments', 'globe', 11),
('Android Notifications', 'android-notifications', 'Firebase Cloud Messaging for Android apps', 'bell', 12),
('React Deployment', 'react-deployment', 'Deploy React applications with Vite', 'code', 13),
('Spring Boot Deployment', 'spring-boot-deployment', 'Deploy Java Spring Boot applications', 'coffee', 14)
ON CONFLICT DO NOTHING;
