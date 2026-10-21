CREATE INDEX idx_tickets_created_by  ON tickets (created_by);
CREATE INDEX idx_tickets_assigned_to ON tickets (assigned_to);
CREATE INDEX idx_tickets_status      ON tickets (status);
CREATE INDEX idx_tickets_created_at  ON tickets (created_at);
CREATE INDEX idx_comments_ticket     ON comments (ticket_id, created_at);