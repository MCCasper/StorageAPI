package wtf.casper.storageapi;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import wtf.casper.storageapi.id.Id;
import wtf.casper.storageapi.id.StorageSerialized;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Getter @EqualsAndHashCode @StorageSerialized
public class TestObjectOther {
    @Id
    private final UUID idOther;
    private String nameOther;
    private int ageOther;
    private TestObjectData data;
    private List<TestObjectData> dataList = List.of(
            new TestObjectData("123 Fake Street", "Walmart", "test@test.com", "123-456-7890", ageOther, new TestObjectBalance(100, "USD")),
            new TestObjectData("456 Fake Street", "Target", "nope@nope.com", "098-765-4321", ageOther, new TestObjectBalance(200, "USD")),
            new TestObjectData("789 Fake Street", "Best Buy", "ttt@ttt.com", "111-222-3333", ageOther, new TestObjectBalance(300, "USD"))
    );
    private TestObjectData[] dataArray = new TestObjectData[] {
            new TestObjectData("123 Fake Street", "Walmart", "test@test.com", "123-456-7890", ageOther, new TestObjectBalance(100, "USD")),
            new TestObjectData("456 Fake Street", "Target", "nope@nope.com", "098-765-4321", ageOther, new TestObjectBalance(200, "USD")),
            new TestObjectData("789 Fake Street", "Best Buy", "ttt@ttt.com", "111-222-3333", ageOther, new TestObjectBalance(300, "USD"))
    };
    private List<TestObjectData> emptyDataList = List.of();
    private TestObjectData[] emptyDataArray = new TestObjectData[] {};

    public TestObjectOther(final UUID id) {
        this.idOther = id;
    }

    public TestObjectOther(final UUID id, final String name, final int age, final TestObjectData data) {
        this.idOther = id;
        this.nameOther = name;
        this.ageOther = age;
        this.data = data;
    }

    @Override
    public String toString() {
        return "TestObjectOther{" +
                "idOther=" + idOther +
                ", nameOther='" + nameOther + '\'' +
                ", ageOther=" + ageOther +
                ", data=" + data +
                ", dataList=" + dataList +
                ", dataArray=" + Arrays.toString(dataArray) +
                ", emptyDataList=" + emptyDataList +
                ", emptyDataArray=" + Arrays.toString(emptyDataArray) +
                '}';
    }
}
