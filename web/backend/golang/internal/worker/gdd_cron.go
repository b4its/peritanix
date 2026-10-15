// Package worker hosts the background schedulers (GDD cron, sync processor).
package worker

import (
	"context"
	"fmt"
	"log/slog"
	"time"

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
		slog.Error("gdd-cron schedule error", "err", err)
		return
	}
	c.Start()
	slog.Info("gdd-cron scheduled", "at", "00:00 daily")

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
		slog.Error("gdd-cron list lands", "err", err)
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
			slog.Warn("gdd-cron compute", "land", l.ID, "err", err)
			continue
		}
		gdd, _ := res["accumulated_gdd"].(float64)
		phase, _ := res["current_phase"].(string)
		if err := deps.Lands.UpdateGDD(ctx, l.ID, gdd, domain.LandPhase(phase)); err != nil {
			slog.Error("gdd-cron update", "land", l.ID, "err", err)
			continue
		}
		// Persist today's point into the time series for the dashboard chart.
		tMin, tMax := todayTemps()
		dayGDD := gddStep(tMin, tMax)
		today := time.Now().Format("2006-01-02")
		if err := deps.GDD.AppendDaily(ctx, l.ID, today, tMin, tMax, dayGDD, gdd); err != nil {
			slog.Warn("gdd-cron append", "land", l.ID, "err", err)
		}
		slog.Info("gdd-cron updated", "land", l.ID, "gdd", gdd, "phase", phase)
	}
}

// todayTemps returns a plausible tropical daily min/max (offline default).
// A real deployment substitutes BMKG / satellite data per land coordinates.
func todayTemps() (tMin, tMax float64) {
	n := float64(time.Now().YearDay())
	return 20.0 + float64(int(n)%3), 30.0 + float64(int(n)%4)
}

// gddStep applies the chilli GDD formula: max(0, (tMax+tMin)/2 - 15).
func gddStep(tMin, tMax float64) float64 {
	v := (tMax+tMin)/2.0 - 15.0
	if v < 0 {
		return 0
	}
	return v
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
