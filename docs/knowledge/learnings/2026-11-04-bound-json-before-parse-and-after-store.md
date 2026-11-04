tags: [json, jackson, security, rooms]
Request-body limits alone are not enough: a config that passed validation could be written, then fail to read back (500) and lock the room.
Fix: cap config size on every write path (64 KB), read stored jsonb through a separate unlimited mapper (StoredJson), and limit depth/size app-wide.
