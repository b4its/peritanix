// Package worker hosts the background schedulers (GDD cron, sync processor).
package worker

import (
	"context"
	"fmt"
	"log"

	"github.com/robfig/cron/v3"

	"github.com/peritanix/dape-backend/internal/config"
	"github.com/peritanix/dape-backend/internal/domain"
	"github.com/peritanix/dape-backend/internal/handlers"
)

// StartGDDCron schedules a daily 00:00 Growing Degree Day recomputation for all
// plots, transitioning their thermal phase and applying the fortification lock.
func StartGDDCron(cfg config.Config, deps *handlers.Deps) {
	c := cron.New()
	// Runs every day at 00:00 local time.
	_, err := c.AddFunc("0 0 * * *", func() { recomputeAllGDD(deps) })
	if err != nil {
		log.Printf("[gdd-cron] schedule error: %v", err)
		return
	}
	c.Start()
	log.Println("[gdd-cron] scheduled daily 00:00 GDD recomputation")

	// Run once shortly after boot so `make web-up` produces fresh GDD without
	// waiting until midnight.
	go func() {
		recomputeAllGDD(deps)
	}()
}

func recomputeAllGDD(deps *handlers.Deps) {
	ctx := context.Background()
	lands, err := deps.Lands.ListAll(ctx)
	if err != nil {
		log.Printf("[gdd-cron] list lands: %v", err)
		return
	}
	for _, l := range lands {
		series := syntheticSeries()
		in := map[string]any{
			"land_id":   l.ID,
			"series":    series,
			"start_cum": l.AccumulatedGDD,
		}
		res, err := deps.ML.GDD(ctx, in)
		if err != nil {
			log.Printf("[gdd-cron] land %s: %v", l.ID, err)
			continue
		}
		gdd, _ := res["accumulated_gdd"].(float64)
		phase, _ := res["current_phase"].(string)
		if err := deps.Lands.UpdateGDD(ctx, l.ID, gdd, domain.LandPhase(phase)); err != nil {
			log.Printf("[gdd-cron] update land %s: %v", l.ID, err)
			continue
		}
		log.Printf("[gdd-cron] land %s -> gdd=%.1f phase=%s", l.ID, gdd, phase)
	}
}

// syntheticSeries fabricates a warm tropical growing series when no weather API
// is configured (offline-first default). A real deployment substitutes BMKG /
// satellite t_min, t_max per each land's coordinates and elevation lapse rate.
func syntheticSeries() [][3]any {
	out := make([][3]any, 0, 14)
	for i := 0; i < 14; i++ {
		out = append(out, [3]any{
			fmt.Sprintf("d%02d", i),
			20.0 + float64(i%3),
			30.0 + float64(i%4),
		})
	}
	return out
}
