// Package redis provides idempotency keys, caching and rate-limit primitives.
package redis

import (
	"context"
	"time"

	goredis "github.com/redis/go-redis/v9"
)

// Client wraps a go-redis client.
type Client struct {
	RDB *goredis.Client
}

// New connects to Redis, retrying until reachable.
func New(ctx context.Context, addr string) (*Client, error) {
	rdb := goredis.NewClient(&goredis.Options{Addr: addr})
	var err error
	for i := 0; i < 30; i++ {
		if err = rdb.Ping(ctx).Err(); err == nil {
			return &Client{RDB: rdb}, nil
		}
		time.Sleep(2 * time.Second)
	}
	return nil, err
}

// Close releases the client.
func (c *Client) Close() error { return c.RDB.Close() }

// SeenOrSet implements the idempotency pattern: returns true if the key was
// already present (a duplicate), otherwise sets it with the given TTL.
func (c *Client) SeenOrSet(ctx context.Context, key string, ttl time.Duration) (bool, error) {
	// SET NX returns true only if the key did not exist (i.e. first time).
	ok, err := c.RDB.SetNX(ctx, "idem:"+key, "1", ttl).Result()
	if err != nil {
		return false, err
	}
	return !ok, nil
}

// Get returns a cached string value (empty if missing).
func (c *Client) Get(ctx context.Context, key string) (string, error) {
	v, err := c.RDB.Get(ctx, key).Result()
	if err == goredis.Nil {
		return "", nil
	}
	return v, err
}

// Set caches a string value with TTL.
func (c *Client) Set(ctx context.Context, key, val string, ttl time.Duration) error {
	return c.RDB.Set(ctx, key, val, ttl).Err()
}
