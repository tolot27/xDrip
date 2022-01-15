package com.eveningoutpost.dexdrip.ImportedLibraries.dexcom;

import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.CalRecord;
import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.EGVRecord;
import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.GenericXMLRecord;
import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.MeterRecord;
import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.PageHeader;
import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.SensorRecord;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Services.DexShareCollectionService;

import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.functions.Consumer;

// This code and this particular library are from the NightScout android uploader
// Check them out here: https://github.com/nightscout/android-uploader
// Some of this code may have been modified for use in this project

public class ReadDataShare {
    byte[] accumulatedResponse;
    //KS private ShareTest mShareTest;
    private DexShareCollectionService mCollectionService;

    //KS public ReadDataShare(ShareTest aShareTest){
    //KS     mShareTest = aShareTest;
    //KS }
    public ReadDataShare(DexShareCollectionService collectionService){
        mCollectionService = collectionService;
    }

    public void getRecentEGVs(final Consumer<EGVRecord[]> recordListener) {
        final int recordType = Constants.RECORD_TYPES.EGV_DATA.ordinal();
        final Consumer<byte[]> fullPageListener = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) { ParsePage(read(0,s).getData(), recordType, recordListener); }
        };
        Consumer<Integer> databasePageRangeCaller = new Consumer<Integer>() {
            @Override
            public void accept(Integer s) { readDataBasePage(recordType, s, fullPageListener); }
        };
        readDataBasePageRange(recordType, databasePageRangeCaller);
    }

    public void getRecentMeterRecords(final Consumer<MeterRecord[]> recordListener) {
        final int recordType = Constants.RECORD_TYPES.METER_DATA.ordinal();
        final Consumer<byte[]> fullPageListener = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) { ParsePage(read(0,s).getData(), recordType, recordListener); }
        };
        Consumer<Integer> databasePageRangeCaller = new Consumer<Integer>() {
            @Override
            public void accept(Integer s) { readDataBasePage(recordType, s, fullPageListener); }
        };
        readDataBasePageRange(recordType, databasePageRangeCaller);
    }

    public void getRecentCalRecords(final Consumer<CalRecord[]> recordListener) {
        final int recordType = Constants.RECORD_TYPES.CAL_SET.ordinal();
        final Consumer<byte[]> fullPageListener = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) { ParsePage(read(0,s).getData(), recordType, recordListener); }
        };
        Consumer<Integer> databasePageRangeCaller = new Consumer<Integer>() {
            @Override
            public void accept(Integer s) { readDataBasePage(recordType, s, fullPageListener); }
        };
        readDataBasePageRange(recordType, databasePageRangeCaller);
    }


    public void getRecentSensorRecords(final Consumer<SensorRecord[]> recordListener) {
        final int recordType = Constants.RECORD_TYPES.SENSOR_DATA.ordinal();
        final Consumer<byte[]> fullPageListener = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) { ParsePage(read(0,s).getData(), recordType, recordListener); }
        };
        Consumer<Integer> databasePageRangeCaller = new Consumer<Integer>() {
            @Override
            public void accept(Integer s) { readDataBasePage(recordType, s, fullPageListener); }
        };
        readDataBasePageRange(recordType, databasePageRangeCaller);
    }

    public void getTimeSinceEGVRecord(final EGVRecord egvRecord, final Consumer<Long> timeSinceEgvRecord) {
        Consumer<Long> tempSystemTimeListener = new Consumer<Long>() {
            @Override
            public void accept(Long s) { Single.just(s - egvRecord.getSystemTimeSeconds()).subscribe(timeSinceEgvRecord); }
        };
        readSystemTime(tempSystemTimeListener);
    }

    public void ping(final Consumer<Boolean> pingListener) {
        Consumer<byte[]> pingReader = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) { Single.just(read(0, s).getCommand() == Constants.ACK).subscribe(pingListener); }
        };
        writeCommand(Constants.PING, pingReader);
    }

    public void readBatteryLevel(final Consumer<Integer> batteryLevelListener) {
        Consumer<byte[]> batteryLevelReader = new Consumer<byte[]>() {
            @Override //TODO: find out if this should be wrapped in read(s).getData();
            public void accept(byte[] s) { Single.just(ByteBuffer.wrap(s).order(ByteOrder.LITTLE_ENDIAN).getInt()).subscribe(batteryLevelListener); }
        };
        writeCommand(Constants.READ_BATTERY_LEVEL, batteryLevelReader);
    }

    public void readSerialNumber(final Consumer<String> serialNumberListener) {
        final Consumer<byte[]> manufacturingDataListener = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) {
                Element el = ParsePage(s, Constants.RECORD_TYPES.MANUFACTURING_DATA.ordinal());
                Single.just(el.getAttribute("SerialNumber")).subscribe(serialNumberListener);
            }
        };
        readDataBasePage(Constants.RECORD_TYPES.MANUFACTURING_DATA.ordinal(), 0, manufacturingDataListener);
    }

    public void readDisplayTime(final Consumer<Date> displayTimeListener) {
        Consumer<Long> tempSystemTimeListener = new Consumer<Long>() {
            @Override
            public void accept(Long s) {
                final long systemTime = s;
                Consumer<Long> tempSystemTimeListener = new Consumer<Long>() {
                    @Override
                    public void accept(Long s) {
                        Date dateDisplayTime = Utils.receiverTimeToDate(systemTime + s);
                        Single.just(dateDisplayTime).subscribe(displayTimeListener); }
                };
                readDisplayTimeOffset(tempSystemTimeListener);
            }
        };
        readSystemTime(tempSystemTimeListener);
    }

    public void readSystemTime(final Consumer<Long> systemTimeListener) {
        Consumer<byte[]> systemTimeReader = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) {
                Single.just(Utils.receiverTimeToDate(ByteBuffer.wrap(read(0,s).getData()).order(ByteOrder.LITTLE_ENDIAN).getInt()).getTime()).subscribe(systemTimeListener);
            }
        };
        writeCommand(Constants.READ_SYSTEM_TIME, systemTimeReader);
    }

    public void readDisplayTimeOffset(final Consumer<Long> displayTimeOffsetListener) {
        Consumer<byte[]> displayTimeOffsetReader = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) { Single.just((long) ByteBuffer.wrap(read(0,s).getData()).order(ByteOrder.LITTLE_ENDIAN).getInt()).subscribe(displayTimeOffsetListener); }
        };
        writeCommand(Constants.READ_DISPLAY_TIME_OFFSET, displayTimeOffsetReader);
    }

    private void readDataBasePageRange(int recordType, final Consumer<Integer> databasePageRangeCaller) {
        ArrayList<Byte> payload = new ArrayList<Byte>();
        payload.add((byte) recordType);
        final Consumer<byte[]> databasePageRangeListener = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) {
                Single.just(ByteBuffer.wrap(new ReadPacket(s).getData()).order(ByteOrder.LITTLE_ENDIAN).getInt(4)).subscribe(databasePageRangeCaller);
            }
        };
        writeCommand(Constants.READ_DATABASE_PAGE_RANGE, payload, databasePageRangeListener);
    }

    private <T> T readDataBasePage(final int recordType, int page, final Consumer<byte[]> fullPageListener) {
        byte numOfPages = 1;
        if (page < 0){ throw new IllegalArgumentException("Invalid page requested:" + page); }
        ArrayList<Byte> payload = new ArrayList<Byte>();
        payload.add((byte) recordType);
        byte[] pageInt = ByteBuffer.allocate(4).putInt(page).array();
        payload.add(pageInt[3]);
        payload.add(pageInt[2]);
        payload.add(pageInt[1]);
        payload.add(pageInt[0]);
        payload.add(numOfPages);
        accumulatedResponse = null;
        final Consumer<byte[]> databasePageReader = new Consumer<byte[]>() {
            @Override
            public void accept(byte[] s) {
                Log.d("ReadDataShare", "Database Page Reader received SIZE: " + s.length);
                byte[] temp = s;
                if (accumulatedResponse == null) {
                    accumulatedResponse = s;
                } else {
                    try {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        outputStream.write(accumulatedResponse);
                        outputStream.write(temp);
                        accumulatedResponse = outputStream.toByteArray();
                        Log.d("ReadDataShare", "Combined Response length: " + accumulatedResponse.length);
                    } catch (Exception e) { e.printStackTrace(); }
                }
                if (temp.length < 20) { Single.just(accumulatedResponse).subscribe(fullPageListener).dispose(); }
            }
        };
        writeCommand(Constants.READ_DATABASE_PAGES, payload, databasePageReader);
        return null;
    }

    private void writeCommand(int command, ArrayList<Byte> payload, Consumer<byte[]> responseListener) {
        List<byte[]> packets = new PacketBuilder(command, payload).composeList();
        //KS if(mShareTest != null) { mShareTest.writeCommand(packets, 0, responseListener); }
        //KS else
        if (mCollectionService != null) { mCollectionService.writeCommand(packets, 0, responseListener); }
    }

    private void writeCommand(int command, Consumer<byte[]> responseListener) {
        List<byte[]> packets = new PacketBuilder(command).composeList();
        //KS if(mShareTest != null) { mShareTest.writeCommand(packets, 0, responseListener); }
        //KS else
        if (mCollectionService != null) { mCollectionService.writeCommand(packets, 0, responseListener); }
    }

    private ReadPacket read(int numOfBytes, byte[] readPacket) {
        return new ReadPacket(Arrays.copyOfRange(readPacket, 0, readPacket.length));
    }

    private <T> T ParsePage(byte[] data, int recordType) { return ParsePage(data, recordType, null); }
    private <T> T ParsePage(byte[] data, int recordType, Consumer<T> parsedPageReceiver) {
        int HEADER_LEN = 28;
        PageHeader pageHeader=new PageHeader(data);
        int NUM_REC_OFFSET = 4;
        int numRec = data[NUM_REC_OFFSET];
        int rec_len;

        switch (Constants.RECORD_TYPES.values()[recordType]) {
            case MANUFACTURING_DATA:
                GenericXMLRecord xmlRecord = new GenericXMLRecord(Arrays.copyOfRange(data, HEADER_LEN, data.length - 1));
                if(parsedPageReceiver != null) {
                    Single.just((T) xmlRecord).subscribe(parsedPageReceiver);
                } else {
                    return (T) xmlRecord;
                }
                break;
            case SENSOR_DATA:
                rec_len = 20;
                SensorRecord[] sensorRecords = new SensorRecord[numRec];
                for (int i = 0; i < numRec; i++) {
                    int startIdx = HEADER_LEN + rec_len * i;
                    sensorRecords[i] = new SensorRecord(Arrays.copyOfRange(data, startIdx, startIdx + rec_len - 1));
                }
                if(parsedPageReceiver != null) {
                    Single.just((T) sensorRecords).subscribe(parsedPageReceiver);
                } else {
                    return (T) sensorRecords;
                }
                break;
            case EGV_DATA:
                rec_len = 13;
                EGVRecord[] egvRecords = new EGVRecord[numRec];
                for (int i = 0; i < numRec; i++) {
                    int startIdx = HEADER_LEN + rec_len * i;
                    egvRecords[i] = new EGVRecord(Arrays.copyOfRange(data, startIdx, startIdx + rec_len - 1));
                }
                if(parsedPageReceiver != null) {
                    Single.just((T) egvRecords).subscribe(parsedPageReceiver);
                } else {
                    return (T) egvRecords;
                }
                break;
            case METER_DATA:
                rec_len = 16;
                MeterRecord[] meterRecords = new MeterRecord[numRec];
                for (int i = 0; i < numRec; i++) {
                    int startIdx = HEADER_LEN + rec_len * i;
                    meterRecords[i] = new MeterRecord(Arrays.copyOfRange(data, startIdx, startIdx + rec_len - 1));
                }
                if(parsedPageReceiver != null) {
                    Single.just((T) meterRecords).subscribe(parsedPageReceiver);
                } else {
                    return (T) meterRecords;
                }
                break;
            case CAL_SET:
                rec_len = 249;
                if (pageHeader.getRevision()<=2) { rec_len = 148; }
                CalRecord[] calRecords = new CalRecord[numRec];
                for (int i = 0; i < numRec; i++) {
                    int startIdx = HEADER_LEN + rec_len * i;
                    calRecords[i] = new CalRecord(Arrays.copyOfRange(data, startIdx, startIdx + rec_len - 1));
                }
                if(parsedPageReceiver != null) {
                    Single.just((T) calRecords).subscribe(parsedPageReceiver);
                } else {
                    return (T) calRecords;
                }
                break;
            default:
                break;
        }
        Single.just((T) null).subscribe(parsedPageReceiver);
        return (T) null;
    }
}
