// Package storage abstracts object storage (S3/MinIO or the dev files service).
//
// The DAPE media flow stores burst frames, trans-illumination images, ooze
// videos and TWI rasters under the `dape-raw` bucket. Two backends:
//   - HTTPStore : PUT to a plain HTTP object server (the dev `files` service).
//     MinIO's S3 API also accepts unauthenticated PUT/GET in dev, so
//     this works against MinIO too when opened anonymously.
//   - NoopStore : discards writes (used when storage is not configured).
package storage

import (
	"bytes"
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// Store is the object-storage port.
type Store interface {
	// Put stores an object and returns its public URL.
	Put(ctx context.Context, key string, data []byte, contentType string) (string, error)
}

// HTTPStore writes objects to an HTTP endpoint (PUT base/key).
type HTTPStore struct {
	base   string // e.g. http://files:9000 or http://minio:9000
	bucket string // e.g. dape-raw
	client *http.Client
}

// NewHTTPStore constructs an HTTP object store. baseURL already includes the
// scheme+host (no trailing slash).
func NewHTTPStore(baseURL, bucket string) *HTTPStore {
	return &HTTPStore{
		base:   strings.TrimRight(baseURL, "/"),
		bucket: strings.Trim(bucket, "/"),
		client: &http.Client{Timeout: 60 * time.Second},
	}
}

// Put uploads an object. key may contain slashes; the caller should namespace
// by land id, e.g. "lands/<uuid>/burst/frame0".
func (s *HTTPStore) Put(ctx context.Context, key string, data []byte, contentType string) (string, error) {
	url := fmt.Sprintf("%s/%s/%s", s.base, s.bucket, strings.TrimLeft(key, "/"))
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, url, bytes.NewReader(data))
	if err != nil {
		return "", err
	}
	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	req.ContentLength = int64(len(data))
	resp, err := s.client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return "", fmt.Errorf("storage PUT %s: status %d", url, resp.StatusCode)
	}
	return url, nil
}

// NoopStore discards writes but reports a synthetic URL.
type NoopStore struct{ PublicBase string }

// NewNoopStore constructs a no-op store.
func NewNoopStore(publicBase string) *NoopStore { return &NoopStore{PublicBase: publicBase} }

// Put implements Store.
func (s *NoopStore) Put(_ context.Context, key string, _ []byte, _ string) (string, error) {
	return strings.TrimRight(s.PublicBase, "/") + "/" + strings.TrimLeft(key, "/"), nil
}
