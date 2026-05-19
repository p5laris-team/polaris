package p5laris.item.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.item.core.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_items")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean equipped = false;

    public void addQuantity(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be positive");
        this.quantity += amount;
    }

    public void equip() {
        this.equipped = true;
    }

    public void unequip() {
        this.equipped = false;
    }
}
