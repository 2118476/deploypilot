const express = require('express');
const { createClient } = require('@supabase/supabase-js');

const app = express();
const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY);

app.get('/api/health', (req, res) => res.json({ status: 'ok' }));
app.get('/api/jobs', async (req, res) => {
  const { data } = await supabase.from('jobs').select('*');
  res.json(data);
});

app.listen(process.env.PORT || 8080);
