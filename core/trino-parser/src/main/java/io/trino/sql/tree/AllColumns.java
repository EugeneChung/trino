/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.sql.tree;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class AllColumns
        extends SelectItem
{
    private final List<Identifier> aliases;
    private final Optional<Expression> target;
    private final List<QualifiedName> excludeList;
    private final List<ReplaceItem> replaceList;

    @Deprecated
    public AllColumns()
    {
        this(Optional.empty(), Optional.empty(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
    }

    @Deprecated
    public AllColumns(Expression target, List<Identifier> aliases)
    {
        this(Optional.empty(), Optional.of(target), aliases, ImmutableList.of(), ImmutableList.of());
    }

    @Deprecated
    public AllColumns(Optional<NodeLocation> location, Optional<Expression> target, List<Identifier> aliases)
    {
        this(location, target, aliases, ImmutableList.of(), ImmutableList.of());
    }

    private AllColumns(
            Optional<NodeLocation> location,
            Optional<Expression> target,
            List<Identifier> aliases,
            List<QualifiedName> excludeList,
            List<ReplaceItem> replaceList)
    {
        super(location);
        this.aliases = ImmutableList.copyOf(requireNonNull(aliases, "aliases is null"));
        this.target = requireNonNull(target, "target is null");
        this.excludeList = ImmutableList.copyOf(requireNonNull(excludeList, "excludeList is null"));
        this.replaceList = ImmutableList.copyOf(requireNonNull(replaceList, "replaceList is null"));
    }

    public AllColumns(NodeLocation location)
    {
        this(location, Optional.empty(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
    }

    public AllColumns(NodeLocation location, Optional<Expression> target, List<Identifier> aliases)
    {
        this(location, target, aliases, ImmutableList.of(), ImmutableList.of());
    }

    public AllColumns(
            NodeLocation location,
            Optional<Expression> target,
            List<Identifier> aliases,
            List<QualifiedName> excludeList,
            List<ReplaceItem> replaceList)
    {
        this(Optional.of(location), target, aliases, excludeList, replaceList);
    }

    public List<Identifier> getAliases()
    {
        return aliases;
    }

    public Optional<Expression> getTarget()
    {
        return target;
    }

    public List<QualifiedName> getExcludeList()
    {
        return excludeList;
    }

    public List<ReplaceItem> getReplaceList()
    {
        return replaceList;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context)
    {
        return visitor.visitAllColumns(this, context);
    }

    @Override
    public List<Node> getChildren()
    {
        ImmutableList.Builder<Node> children = ImmutableList.builder();
        target.ifPresent(children::add);
        children.addAll(replaceList);
        return children.build();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AllColumns other = (AllColumns) o;
        return Objects.equals(aliases, other.aliases) &&
                Objects.equals(target, other.target) &&
                Objects.equals(excludeList, other.excludeList) &&
                Objects.equals(replaceList, other.replaceList);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(aliases, target, excludeList, replaceList);
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();

        target.ifPresent(value -> builder.append(value).append("."));
        builder.append("*");

        if (!excludeList.isEmpty()) {
            builder.append(" EXCLUDE (");
            Joiner.on(", ").appendTo(builder, excludeList);
            builder.append(")");
        }

        if (!replaceList.isEmpty()) {
            builder.append(" REPLACE (");
            Joiner.on(", ").appendTo(builder, replaceList);
            builder.append(")");
        }

        if (!aliases.isEmpty()) {
            builder.append(" (");
            Joiner.on(", ").appendTo(builder, aliases);
            builder.append(")");
        }

        return builder.toString();
    }

    @Override
    public boolean shallowEquals(Node other)
    {
        if (!sameClass(this, other)) {
            return false;
        }

        AllColumns that = (AllColumns) other;
        return aliases.equals(that.aliases) &&
                excludeList.equals(that.excludeList);
    }
}
