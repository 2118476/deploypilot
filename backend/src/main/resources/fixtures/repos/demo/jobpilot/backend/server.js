const express = require('express');
const { createClient } = require('@supabase/supabase-js');

const app = express();
const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY);

const router = express.Router();
router.get('/health', (req, res) => res.json({ status: 'ok' }));
router.get('/jobs', async (req, res) => {
  const { data } = await supabase.from('jobs').select('*');
  res.json(data);
});

app.use('/api', router);

const port = process.env.PORT || 8080;
app.listen(port, () => console.log(`JobPilot API on ${port}`));
