package handlers

import (
	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/crdt"
)

// --- CRDT field-level merge -------------------------------------------------

// mergeRecordDTO is a JSON-friendly CRDT record.
type mergeRecordDTO struct {
	ID     string                   `json:"id"`
	VV     map[string]uint64        `json:"vv"`
	Fields map[string]mergeFieldDTO `json:"fields"`
}

type mergeFieldDTO struct {
	Value     any           `json:"value"`
	Timestamp crdtTimestamp `json:"ts"`
}

type crdtTimestamp struct {
	UpdatedTS int64  `json:"updated_ts"`
	Rev       int64  `json:"rev"`
	DeviceID  string `json:"device_id"`
}

type syncMergeReq struct {
	Local  mergeRecordDTO `json:"local"`
	Remote mergeRecordDTO `json:"remote"`
}

type syncMergeResp struct {
	Merged     mergeRecordDTO `json:"merged"`
	Concurrent bool           `json:"concurrent"`
}

// toRecord converts the DTO into a crdt.Record.
func toRecord(dto mergeRecordDTO) *crdt.Record {
	r := crdt.NewRecord(dto.ID)
	if dto.VV != nil {
		r.VV = crdt.VersionVector(dto.VV)
	}
	for k, f := range dto.Fields {
		r.Fields[k] = crdt.FieldValue{
			Value: f.Value,
			Timestamp: crdt.Timestamp{
				UpdatedTS: f.Timestamp.UpdatedTS,
				Rev:       f.Timestamp.Rev,
				DeviceID:  f.Timestamp.DeviceID,
			},
		}
	}
	return r
}

// toDTO converts a crdt.Record back into the JSON DTO.
func toDTO(r *crdt.Record) mergeRecordDTO {
	dto := mergeRecordDTO{ID: r.ID, VV: map[string]uint64{}, Fields: map[string]mergeFieldDTO{}}
	for k, v := range r.VV {
		dto.VV[k] = v
	}
	for k, f := range r.Fields {
		dto.Fields[k] = mergeFieldDTO{
			Value: f.Value,
			Timestamp: crdtTimestamp{
				UpdatedTS: f.Timestamp.UpdatedTS,
				Rev:       f.Timestamp.Rev,
				DeviceID:  f.Timestamp.DeviceID,
			},
		}
	}
	return dto
}

// syncMerge merges a local record with the server/remote record using CRDT
// field-level Last-Write-Wins with a version-vector concurrency check. The
// operation is commutative/associative/idempotent, so clients can retry freely.
func (d *Deps) syncMerge(c *fiber.Ctx) error {
	var req syncMergeReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	local := toRecord(req.Local)
	remote := toRecord(req.Remote)

	concurrent := crdt.Concurrent(local.VV, remote.VV)
	merged := crdt.Merge(local, remote)

	return c.JSON(syncMergeResp{
		Merged:     toDTO(merged),
		Concurrent: concurrent,
	})
}
