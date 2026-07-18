package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import java.util.List;

public record TableDeletionImpact(List<TableDependency> dependencies) {
    public boolean hasDependents() { return !dependencies.isEmpty(); }
}
